/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.source.tree.java;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettingsFacade;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.SourceJavaCodeReference;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.resolve.*;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.DelegatingScopeProcessor;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.scope.MethodProcessorSetupFailedException;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.MethodResolverProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PsiReferenceExpressionImpl extends ExpressionPsiElement implements PsiReferenceExpression, SourceJavaCodeReference {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl");

  private volatile String myCachedQName;
  private volatile String myCachedNormalizedText;

  public PsiReferenceExpressionImpl() {
    super(JavaElementType.REFERENCE_EXPRESSION);
  }

  @Override
  public PsiExpression getQualifierExpression() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.QUALIFIER);
  }

  @Override
  public PsiElement bindToElementViaStaticImport(@NotNull PsiClass qualifierClass) throws IncorrectOperationException {
    String qualifiedName = qualifierClass.getQualifiedName();
    if (qualifiedName == null) throw new IncorrectOperationException();

    if (getQualifierExpression() != null) {
      throw new IncorrectOperationException("Reference is qualified: "+getText());
    }
    if (!isPhysical()) {
      // don't qualify reference: the isReferenceTo() check fails anyway, whether we have a static import for this member or not
      return this;
    }
    String staticName = getReferenceName();
    PsiFile containingFile = getContainingFile();
    PsiImportList importList = null;
    boolean doImportStatic;
    if (containingFile instanceof PsiJavaFile) {
      importList = ((PsiJavaFile)containingFile).getImportList();
      assert importList != null : containingFile;
      PsiImportStatementBase singleImportStatement = importList.findSingleImportStatement(staticName);
      doImportStatic = singleImportStatement == null;
      if (singleImportStatement instanceof PsiImportStaticStatement) {
        String qName = qualifierClass.getQualifiedName() + "." + staticName;
        if (qName.equals(singleImportStatement.getImportReference().getQualifiedName())) return this;
      }
    }
    else {
      doImportStatic = false;
    }
    if (doImportStatic) {
      bindToElementViaStaticImport(qualifierClass, staticName, importList);
    }
    else {
      PsiManagerEx manager = getManager();
      PsiReferenceExpression classRef = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createReferenceExpression(
        qualifierClass);
      final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
      LeafElement dot = Factory.createSingleLeafElement(JavaTokenType.DOT, ".", 0, 1, treeCharTab, manager);
      addInternal(dot, dot, SourceTreeToPsiMap.psiElementToTree(getParameterList()), Boolean.TRUE);
      addBefore(classRef, SourceTreeToPsiMap.treeElementToPsi(dot));
    }
    return this;
  }

  public static void bindToElementViaStaticImport(PsiClass qualifierClass, String staticName, PsiImportList importList) throws IncorrectOperationException {
    assert importList != null;
    final String qualifiedName  = qualifierClass.getQualifiedName();
    final List<PsiJavaCodeReferenceElement> refs = getImportsFromClass(importList, qualifiedName);
    if (refs.size() < JavaCodeStyleSettingsFacade.getInstance(qualifierClass.getProject()).getNamesCountToUseImportOnDemand() ||
        JavaCodeStyleManager.getInstance(qualifierClass.getProject()).hasConflictingOnDemandImport((PsiJavaFile)importList.getContainingFile(), qualifierClass, staticName)) {
      importList.add(JavaPsiFacade.getInstance(qualifierClass.getProject()).getElementFactory().createImportStaticStatement(qualifierClass, staticName));
    } else {
      for (PsiJavaCodeReferenceElement ref : refs) {
        final PsiImportStaticStatement importStatement = PsiTreeUtil.getParentOfType(ref, PsiImportStaticStatement.class);
        if (importStatement != null) {
          importStatement.delete();
        }
      }
      importList.add(JavaPsiFacade.getInstance(qualifierClass.getProject()).getElementFactory().createImportStaticStatement(qualifierClass,
                                                                                                                            "*"));
    }
  }

  private static List<PsiJavaCodeReferenceElement> getImportsFromClass(@NotNull PsiImportList importList, String className){
    final List<PsiJavaCodeReferenceElement> array = new ArrayList<>();
    for (PsiImportStaticStatement staticStatement : importList.getImportStaticStatements()) {
      final PsiClass psiClass = staticStatement.resolveTargetClass();
      if (psiClass != null && Comparing.strEqual(psiClass.getQualifiedName(), className)) {
        array.add(staticStatement.getImportReference());
      }
    }
    return array;
  }

  @Override
  public void setQualifierExpression(@Nullable PsiExpression newQualifier) throws IncorrectOperationException {
    final PsiExpression oldQualifier = getQualifierExpression();
    if (newQualifier == null) {
      if (oldQualifier != null) {
        deleteChildInternal(oldQualifier.getNode());
      }
    }
    else {
      if (oldQualifier != null) {
        oldQualifier.replace(newQualifier);
      }
      else {
        final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
        TreeElement dot = (TreeElement)findChildByRole(ChildRole.DOT);
        if (dot == null) {
          dot = Factory.createSingleLeafElement(JavaTokenType.DOT, ".", 0, 1, treeCharTab, getManager());
          dot = addInternal(dot, dot, getFirstChildNode(), Boolean.TRUE);
        }
        addBefore(newQualifier, dot.getPsi());
      }
    }
  }

  @Override
  public PsiElement getQualifier() {
    return getQualifierExpression();
  }

  @Override
  public String getReferenceName() {
    PsiElement element = getReferenceNameElement();
    return element != null ? element.getText() : null;
  }

  @Override
  public void clearCaches() {
    myCachedQName = null;
    myCachedNormalizedText = null;
    super.clearCaches();
  }

  public static final class OurGenericsResolver implements ResolveCache.PolyVariantContextResolver<PsiJavaReference> {
    public static final OurGenericsResolver INSTANCE = new OurGenericsResolver();

    @NotNull
    @Override
    public ResolveResult[] resolve(@NotNull PsiJavaReference ref, @NotNull PsiFile containingFile, boolean incompleteCode) {
      PsiReferenceExpressionImpl expression = (PsiReferenceExpressionImpl)ref;
      CompositeElement treeParent = expression.getTreeParent();
      IElementType parentType = treeParent == null ? null : treeParent.getElementType();

      List<ResolveResult[]> qualifiers = resolveAllQualifiers(expression, containingFile);
      JavaResolveResult[] result = expression.resolve(parentType, containingFile);

      if (result.length == 0 && incompleteCode && parentType != JavaElementType.REFERENCE_EXPRESSION) {
        result = expression.resolve(JavaElementType.REFERENCE_EXPRESSION, containingFile);
      }

      JavaResolveUtil.substituteResults(expression, result);

      qualifiers.clear(); // hold qualifier target list until this moment to avoid psi elements inside to GC

      return result;
    }

    @NotNull
    private static List<ResolveResult[]> resolveAllQualifiers(@NotNull PsiReferenceExpressionImpl expression, @NotNull final PsiFile containingFile) {
      // to avoid SOE, resolve all qualifiers starting from the innermost
      PsiElement qualifier = expression.getQualifier();
      if (qualifier == null) return Collections.emptyList();

      final List<ResolveResult[]> qualifiers = new SmartList<>();
      final ResolveCache resolveCache = ResolveCache.getInstance(containingFile.getProject());
      qualifier.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          if (!(expression instanceof PsiReferenceExpressionImpl)) {
            return;
          }
          ResolveResult[] cachedResults = resolveCache.getCachedResults(expression, true, false, true);
          if (cachedResults != null) {
            return;
          }
          visitElement(expression);
        }

        @Override
        protected void elementFinished(@NotNull PsiElement element) {
          if (!(element instanceof PsiReferenceExpressionImpl)) return;
          PsiReferenceExpressionImpl expression = (PsiReferenceExpressionImpl)element;
          qualifiers.add(resolveCache.resolveWithCaching(expression, INSTANCE, false, false, containingFile));
        }

        // walk only qualifiers, not their argument and other associated stuff

        @Override
        public void visitExpressionList(PsiExpressionList list) { }

        @Override
        public void visitLambdaExpression(PsiLambdaExpression expression) { }

        @Override
        public void visitClass(PsiClass aClass) { }
      });
      return qualifiers;
    }
  }

  @NotNull
  private JavaResolveResult[] resolve(IElementType parentType, @NotNull PsiFile containingFile) {
    if (parentType == JavaElementType.REFERENCE_EXPRESSION) {
      JavaResolveResult[] variable = null;
      JavaResolveResult[] result = resolveToVariable(containingFile);
      if (result.length == 1) {
        if (result[0].isAccessible()) {
          return result;
        }
        variable = result;
      }

      PsiElement classNameElement = getReferenceNameElement();
      if (!(classNameElement instanceof PsiIdentifier)) {
        return JavaResolveResult.EMPTY_ARRAY;
      }

      result = resolveToClass(classNameElement, containingFile);
      if (result.length == 1 && !result[0].isAccessible()) {
        JavaResolveResult[] packageResult = resolveToPackage(containingFile);
        if (packageResult.length != 0) {
          result = packageResult;
        }
      }
      else if (result.length == 0) {
        result = resolveToPackage(containingFile);
      }

      return result.length == 0 && variable != null ? variable : result;
    }

    if (parentType == JavaElementType.METHOD_CALL_EXPRESSION) {
      return resolveToMethod(containingFile);
    }

    if (parentType == JavaElementType.METHOD_REF_EXPRESSION) {
      if (((PsiMethodReferenceExpression)getParent()).isConstructor()) {
        PsiElement classNameElement = getReferenceNameElement();
        if (classNameElement == null) {
          return JavaResolveResult.EMPTY_ARRAY;
        }
        return resolveToClass(classNameElement, containingFile);
      }
      return resolve(JavaElementType.REFERENCE_EXPRESSION, containingFile);
    }

    return resolveToVariable(containingFile);
  }

  @NotNull
  private JavaResolveResult[] resolveToMethod(@NotNull PsiFile containingFile) {
    final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)getParent();
    final MethodResolverProcessor processor = new MethodResolverProcessor(methodCall, containingFile);
    try {
      PsiScopesUtil.setupAndRunProcessor(processor, methodCall, false);
    }
    catch (MethodProcessorSetupFailedException e) {
      return JavaResolveResult.EMPTY_ARRAY;
    }
    return processor.getResult();
  }

  @NotNull
  private JavaResolveResult[] resolveToPackage(@NotNull PsiFile containingFile) {
    final String packageName = getCachedNormalizedText();
    Project project = containingFile.getProject();
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiPackage aPackage = psiFacade.findPackage(packageName);
    if (aPackage == null) {
      return psiFacade.isPartOfPackagePrefix(packageName)
             ? CandidateInfo.RESOLVE_RESULT_FOR_PACKAGE_PREFIX_PACKAGE
             : JavaResolveResult.EMPTY_ARRAY;
    }
    // check that all qualifiers must resolve to package parts, to prevent local vars shadowing corresponding package case
    PsiExpression qualifier = getQualifierExpression();
    if (qualifier instanceof PsiReferenceExpression && !(((PsiReferenceExpression)qualifier).resolve() instanceof PsiPackage)) {
      return JavaResolveResult.EMPTY_ARRAY;
    }
    return new JavaResolveResult[]{new CandidateInfo(aPackage, PsiSubstitutor.EMPTY, this, false)};
  }

  @NotNull
  private JavaResolveResult[] resolveToClass(@NotNull PsiElement classNameElement, @NotNull PsiFile containingFile) {
    final String className = classNameElement.getText();

    final ClassResolverProcessor processor = new ClassResolverProcessor(className, this, containingFile);
    PsiScopesUtil.resolveAndWalk(processor, this, null);
    return processor.getResult();
  }

  @NotNull
  private JavaResolveResult[] resolveToVariable(@NotNull PsiFile containingFile) {
    final VariableResolverProcessor processor = new VariableResolverProcessor(this, containingFile);
    PsiScopesUtil.resolveAndWalk(processor, this, null);
    return processor.getResult();
  }

  @Override
  @NotNull
  public JavaResolveResult[] multiResolve(boolean incompleteCode) {
    return PsiImplUtil.multiResolveImpl(this, incompleteCode, OurGenericsResolver.INSTANCE);
  }

  @Override
  @NotNull
  public String getCanonicalText() {
    final PsiElement element = resolve();
    if (element instanceof PsiClass) {
      final String fqn = ((PsiClass)element).getQualifiedName();
      if (fqn != null) return fqn;
    }
    return getCachedNormalizedText();
  }

  private static final Function<PsiReferenceExpressionImpl, PsiType> TYPE_EVALUATOR = new TypeEvaluator();

  private static class TypeEvaluator implements NullableFunction<PsiReferenceExpressionImpl, PsiType> {
    @Override
    public PsiType fun(final PsiReferenceExpressionImpl expr) {
      PsiFile file = expr.getContainingFile();
      Project project = file.getProject();
      ResolveResult[] results = ResolveCache.getInstance(project).resolveWithCaching(expr, OurGenericsResolver.INSTANCE, true, false, file);
      JavaResolveResult result = results.length == 1 ? (JavaResolveResult)results[0] : null;

      PsiElement resolve = result == null ? null : result.getElement();
      if (resolve == null) {
        ASTNode refName = expr.findChildByRole(ChildRole.REFERENCE_NAME);
        if (refName != null && "length".equals(refName.getText())) {
          ASTNode qualifier = expr.findChildByRole(ChildRole.QUALIFIER);
          if (qualifier != null && ElementType.EXPRESSION_BIT_SET.contains(qualifier.getElementType())) {
            PsiType type = SourceTreeToPsiMap.<PsiExpression>treeToPsiNotNull(qualifier).getType();
            if (type instanceof PsiArrayType) {
              return PsiType.INT;
            }
          }
        }
        return null;
      }

      PsiTypeParameterListOwner owner = null;
      PsiType ret = null;
      if (resolve instanceof PsiVariable) {
        PsiType type = ((PsiVariable)resolve).getType();
        ret = type instanceof PsiEllipsisType ? ((PsiEllipsisType)type).toArrayType() : type;
        if (ret != null && !ret.isValid()) {
          LOG.error("invalid type of " + resolve + " of class " + resolve.getClass() + ", valid=" + resolve.isValid());
        }
        if (resolve instanceof PsiField && !((PsiField)resolve).hasModifierProperty(PsiModifier.STATIC)) {
          owner = ((PsiField)resolve).getContainingClass();
        }
      }
      else if (resolve instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)resolve;
        ret = method.getReturnType();
        if (ret != null) {
          PsiUtil.ensureValidType(ret);
        }
        owner = method;
      }
      if (ret == null) return null;

      final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(file);
      if (ret instanceof PsiClassType) {
        ret = ((PsiClassType)ret).setLanguageLevel(languageLevel);
      }

      if (languageLevel.isAtLeast(LanguageLevel.JDK_1_5)) {
        final PsiSubstitutor substitutor = result.getSubstitutor();
        if (owner == null || !PsiUtil.isRawSubstitutor(owner, substitutor)) {
          PsiType substitutedType = substitutor.substitute(ret);
          PsiUtil.ensureValidType(substitutedType);
          PsiType normalized = PsiImplUtil.normalizeWildcardTypeByPosition(substitutedType, expr);
          PsiUtil.ensureValidType(normalized);
          return PsiClassImplUtil.correctType(normalized, expr.getResolveScope());
        }
      }

      return PsiClassImplUtil.correctType(TypeConversionUtil.erasure(ret), expr.getResolveScope());
    }
  }

  @Override
  public PsiType getType() {
    return JavaResolveCache.getInstance(getProject()).getType(this, TYPE_EVALUATOR);
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    IElementType i = getLastChildNode().getElementType();
    boolean resolvingToMethod = element instanceof PsiMethod;
    if (i == JavaTokenType.IDENTIFIER) {
      if (!(element instanceof PsiPackage)) {
        if (!(element instanceof PsiNamedElement)) return false;
        String name = ((PsiNamedElement)element).getName();
        if (name == null) return false;
        if (!name.equals(getLastChildNode().getText())) return false;
      }
    }
    else if (i == JavaTokenType.SUPER_KEYWORD || i == JavaTokenType.THIS_KEYWORD) {
      if (!resolvingToMethod) return false;
      if (!((PsiMethod)element).isConstructor()) return false;
    }

    PsiElement parent = getParent();
    boolean parentIsMethodCall = parent instanceof PsiMethodCallExpression;
    // optimization: methodCallExpression should resolve to a method
    if (parentIsMethodCall != resolvingToMethod) return false;

    return element.getManager().areElementsEquivalent(element, advancedResolve(true).getElement());
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    // this reference's variants are rather obtained with processVariants()
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  public void processVariants(@NotNull PsiScopeProcessor processor) {
    DelegatingScopeProcessor filterProcessor = new DelegatingScopeProcessor(processor) {
      private PsiElement myResolveContext;
      private final Set<String> myVarNames = new THashSet<>();

      @Override
      public boolean execute(@NotNull final PsiElement element, @NotNull final ResolveState state) {
        return !shouldProcess(element) || super.execute(element, state);
      }

      private boolean shouldProcess(@NotNull PsiElement element) {
        if (element instanceof PsiVariable) return ensureNonShadowedVariable((PsiVariable)element);
        if (element instanceof PsiClass) return !seemsScrambled((PsiClass)element);
        if (element instanceof PsiPackage) return isQualified();
        if (element instanceof PsiMethod) return shouldProcessMethod((PsiMethod)element);
        return false;
      }

      private boolean ensureNonShadowedVariable(@NotNull PsiVariable element) {
        if (element instanceof PsiLocalVariable || element instanceof PsiParameter) {
          myVarNames.add(element.getName());
        }
        if (element instanceof PsiField && myVarNames.contains(element.getName())) {
          return false;
        }
        return true;
      }

      private boolean shouldProcessMethod(@NotNull PsiMethod method) {
        PsiReferenceExpressionImpl ref = PsiReferenceExpressionImpl.this;
        return !method.isConstructor() && hasValidQualifier(method, ref, myResolveContext);
      }

      @Override
      public void handleEvent(@NotNull Event event, Object associated) {
        if (event == JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT) {
          myResolveContext = (PsiElement)associated;
        }
        super.handleEvent(event, associated);
      }

    };
    PsiScopesUtil.resolveAndWalk(filterProcessor, this, null, true);
  }

  @NotNull
  @Override
  public JavaResolveResult advancedResolve(boolean incompleteCode) {
    JavaResolveResult[] results = multiResolve(incompleteCode);
    return results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
  }

  /* see also HighlightMethodUtil.checkStaticInterfaceMethodCallQualifier() */
  private static boolean hasValidQualifier(PsiMethod method, PsiReferenceExpression ref, PsiElement scope) {
    PsiClass containingClass = method.getContainingClass();
    if (containingClass != null && containingClass.isInterface() && method.hasModifierProperty(PsiModifier.STATIC)) {
      if (!PsiUtil.getLanguageLevel(ref).isAtLeast(LanguageLevel.JDK_1_8)) {
        return false;
      }

      PsiExpression qualifierExpression = ref.getQualifierExpression();
      if (qualifierExpression == null && (scope instanceof PsiImportStaticStatement || PsiTreeUtil.isAncestor(containingClass, ref, true))) {
        return true;
      }

      if (qualifierExpression instanceof PsiReferenceExpression) {
        PsiElement resolve = ((PsiReferenceExpression)qualifierExpression).resolve();
        if (resolve == containingClass) {
          return true;
        }

        if (resolve instanceof PsiTypeParameter) {
          Set<PsiClass> classes = new HashSet<>();
          for (PsiClassType type : ((PsiTypeParameter)resolve).getExtendsListTypes()) {
            final PsiClass aClass = type.resolve();
            if (aClass != null) {
              classes.add(aClass);
            }
          }

          if (classes.size() == 1 && classes.contains(containingClass)) {
            return true;
          }
        }
      }

      return false;
    }

    return true;
  }

  public static boolean seemsScrambled(@Nullable PsiClass aClass) {
    return aClass instanceof PsiCompiledElement && seemsScrambledByStructure(aClass);
  }

  @VisibleForTesting
  public static boolean seemsScrambledByStructure(@NotNull PsiClass aClass) {
    PsiClass containingClass = aClass.getContainingClass();
    if (containingClass != null && !seemsScrambledByStructure(containingClass)) {
      return false;
    }

    if (seemsScrambled(aClass.getName())) {
      List<PsiMethod> methods = ContainerUtil.filter(aClass.getMethods(), method -> !method.hasModifierProperty(PsiModifier.PRIVATE));

      return !methods.isEmpty() && ContainerUtil.and(methods, method -> seemsScrambled(method.getName()));
    }

    return false;
  }

  private static boolean seemsScrambled(String name) {
    return name != null && !name.isEmpty() && name.length() <= 2;
  }

  @Override
  public PsiElement getReferenceNameElement() {
    return findChildByRoleAsPsiElement(ChildRole.REFERENCE_NAME);
  }

  @Override
  public PsiReferenceParameterList getParameterList() {
    return PsiTreeUtil.getChildOfType(this, PsiReferenceParameterList.class);
  }

  @NotNull
  @Override
  public PsiType[] getTypeParameters() {
    PsiReferenceParameterList parameterList = getParameterList();
    return parameterList != null ? parameterList.getTypeArguments() : PsiType.EMPTY_ARRAY;
  }

  @Override
  public int getTextOffset() {
    ASTNode refName = findChildByRole(ChildRole.REFERENCE_NAME);
    return refName == null ? super.getTextOffset() : refName.getStartOffset();
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    if (getQualifierExpression() != null) {
      return renameDirectly(newElementName);
    }
    final JavaResolveResult resolveResult = advancedResolve(false);
    if (resolveResult.getElement() == null) {
      return renameDirectly(newElementName);
    }
    PsiElement currentFileResolveScope = resolveResult.getCurrentFileResolveScope();
    if (!(currentFileResolveScope instanceof PsiImportStaticStatement) ||
        ((PsiImportStaticStatement)currentFileResolveScope).isOnDemand()) {
      return renameDirectly(newElementName);
    }
    final PsiImportStaticStatement importStaticStatement = (PsiImportStaticStatement)currentFileResolveScope;
    final String referenceName = importStaticStatement.getReferenceName();
    LOG.assertTrue(referenceName != null);
    final PsiElement element = importStaticStatement.getImportReference().resolve();
    if (getManager().areElementsEquivalent(element, resolveResult.getElement())) {
      return renameDirectly(newElementName);
    }
    final PsiClass psiClass = importStaticStatement.resolveTargetClass();
    if (psiClass == null) return renameDirectly(newElementName);
    final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
    final PsiReferenceExpression expression = (PsiReferenceExpression)factory.createExpressionFromText("X." + newElementName, this);
    final PsiReferenceExpression result = (PsiReferenceExpression)replace(expression);
    ((PsiReferenceExpression)result.getQualifierExpression()).bindToElement(psiClass);
    return result;
  }

  private PsiElement renameDirectly(String newElementName) throws IncorrectOperationException {
    PsiElement oldIdentifier = findChildByRoleAsPsiElement(ChildRole.REFERENCE_NAME);
    if (oldIdentifier == null) {
      throw new IncorrectOperationException();
    }
    final String oldRefName = oldIdentifier.getText();
    if (PsiKeyword.THIS.equals(oldRefName) || PsiKeyword.SUPER.equals(oldRefName) || Comparing.strEqual(oldRefName, newElementName)) return this;
    PsiIdentifier identifier = JavaPsiFacade.getInstance(getProject()).getElementFactory().createIdentifier(newElementName);
    oldIdentifier.replace(identifier);
    return this;
  }

  @Override
  public PsiElement bindToElement(@NotNull final PsiElement element) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);

    if (isReferenceTo(element)) return this;

    final PsiManager manager = getManager();
    final PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(getProject()).getParserFacade();
    if (element instanceof PsiClass) {
      final boolean preserveQualification = JavaCodeStyleSettingsFacade.getInstance(getProject()).useFQClassNames() && isFullyQualified(this);
      String qName = ((PsiClass)element).getQualifiedName();
      if (qName == null) {
        qName = ((PsiClass)element).getName();
      }
      else if (JavaPsiFacade.getInstance(manager.getProject()).findClass(qName, getResolveScope()) == null && !preserveQualification) {
        return this;
      }
      PsiExpression ref = parserFacade.createExpressionFromText(qName, this);
      getTreeParent().replaceChildInternal(this, (TreeElement)ref.getNode());
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(manager.getProject());
      if (!preserveQualification) {
        ref = (PsiExpression)codeStyleManager.shortenClassReferences(ref, JavaCodeStyleManager.INCOMPLETE_CODE);
      }
      return ref;
    }
    else if (element instanceof PsiPackage) {
      final String qName = ((PsiPackage)element).getQualifiedName();
      if (qName.isEmpty()) {
        throw new IncorrectOperationException();
      }
      final PsiExpression ref = parserFacade.createExpressionFromText(qName, this);
      getTreeParent().replaceChildInternal(this, (TreeElement)ref.getNode());
      return ref;
    }
    else if ((element instanceof PsiField || element instanceof PsiMethod) && ((PsiMember) element).hasModifierProperty(PsiModifier.STATIC)) {
      if (!isPhysical()) {
        // don't qualify reference: the isReferenceTo() check fails anyway, whether we have a static import for this member or not
        return this;
      }
      final PsiMember member = (PsiMember) element;
      final PsiClass psiClass = member.getContainingClass();
      if (psiClass == null) throw new IncorrectOperationException();
      final String qName = psiClass.getQualifiedName() + "." + member.getName();
      final PsiExpression ref = parserFacade.createExpressionFromText(qName, this);
      getTreeParent().replaceChildInternal(this, (TreeElement)ref.getNode());
      return ref;
    }
    else {
      throw new IncorrectOperationException(element.toString());
    }
  }

  private static boolean isFullyQualified(CompositeElement classRef) {
    ASTNode qualifier = classRef.findChildByRole(ChildRole.QUALIFIER);
    if (qualifier == null) return false;
    if (qualifier.getElementType() != JavaElementType.REFERENCE_EXPRESSION) return false;
    PsiElement refElement = ((PsiReference)qualifier).resolve();
    return refElement instanceof PsiPackage || isFullyQualified((CompositeElement)qualifier);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (getChildRole(child) == ChildRole.QUALIFIER) {
      ASTNode dot = findChildByRole(ChildRole.DOT);
      super.deleteChildInternal(child);
      deleteChildInternal(dot);
    }
    else if (child.getElementType() == JavaElementType.REFERENCE_PARAMETER_LIST) {
      replaceChildInternal(child, createEmptyRefParameterList(getProject()));
    }
    else {
      super.deleteChildInternal(child);
    }
  }

  public static TreeElement createEmptyRefParameterList(Project project) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    return (TreeElement)Objects.requireNonNull(factory.createReferenceFromText("foo", null).getParameterList()).getNode();
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      default:
        return null;

      case ChildRole.REFERENCE_NAME:
        if (getChildRole(getLastChildNode()) == role) {
          return getLastChildNode();
        }

        return findChildByType(JavaTokenType.IDENTIFIER);

      case ChildRole.QUALIFIER:
        if (getChildRole(getFirstChildNode()) == ChildRole.QUALIFIER) {
          return getFirstChildNode();
        }

        return null;

      case ChildRole.REFERENCE_PARAMETER_LIST:
        return findChildByType(JavaElementType.REFERENCE_PARAMETER_LIST);

      case ChildRole.DOT:
        return findChildByType(JavaTokenType.DOT);
    }
  }

  @Override
  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaTokenType.DOT) {
      return ChildRole.DOT;
    }
    else if (i == JavaElementType.REFERENCE_PARAMETER_LIST) {
      return ChildRole.REFERENCE_PARAMETER_LIST;
    }
    else if (i == JavaTokenType.IDENTIFIER || i == JavaTokenType.THIS_KEYWORD || i == JavaTokenType.SUPER_KEYWORD) {
      return ChildRole.REFERENCE_NAME;
    }
    else {
      if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.QUALIFIER;
      }
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public PsiReference getReference() {
    if (getReferenceNameElement() == null) return null;
    
    return this;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReferenceExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiElement getElement() {
    return this;
  }

  @Override
  public TextRange getRangeInElement() {
    return PsiJavaCodeReferenceElementImpl.calcRangeInElement(this);
  }

  @Override
  public PsiElement resolve() {
    return advancedResolve(false).getElement();
  }

  @Override
  public String getClassNameText() {
    String cachedQName = myCachedQName;
    if (cachedQName == null) {
      myCachedQName = cachedQName = PsiNameHelper.getQualifiedClassName(getCachedNormalizedText(), false);
    }
    return cachedQName;
  }

  @Override
  public void fullyQualify(@NotNull PsiClass targetClass) {
    JavaSourceUtil.fullyQualifyReference(this, targetClass);
  }

  @Override
  public boolean isQualified() {
    return getChildRole(getFirstChildNode()) == ChildRole.QUALIFIER;
  }

  @Override
  public String getQualifiedName() {
    return getCanonicalText();
  }

  private String getCachedNormalizedText() {
    String whiteSpaceAndComments = myCachedNormalizedText;
    if (whiteSpaceAndComments == null) {
      myCachedNormalizedText = whiteSpaceAndComments = JavaSourceUtil.getReferenceText(this);
    }
    return whiteSpaceAndComments;
  }

  @Override
  public String toString() {
    return "PsiReferenceExpression:" + getText();
  }
}