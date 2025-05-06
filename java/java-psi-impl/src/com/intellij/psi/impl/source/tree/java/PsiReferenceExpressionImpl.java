// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettingsFacade;
import com.intellij.psi.codeStyle.JavaFileCodeStyleFacade;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.SourceJavaCodeReference;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PsiReferenceExpressionImpl extends ExpressionPsiElement implements PsiReferenceExpression, SourceJavaCodeReference {
  private static final Logger LOG = Logger.getInstance(PsiReferenceExpressionImpl.class);

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
      PsiReferenceExpression classRef = JavaPsiFacade.getElementFactory(manager.getProject()).createReferenceExpression(
        qualifierClass);
      CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
      LeafElement dot = Factory.createSingleLeafElement(JavaTokenType.DOT, ".", 0, 1, treeCharTab, manager);
      addInternal(dot, dot, SourceTreeToPsiMap.psiElementToTree(getParameterList()), Boolean.TRUE);
      addBefore(classRef, SourceTreeToPsiMap.treeElementToPsi(dot));
    }
    return this;
  }

  public static void bindToElementViaStaticImport(@NotNull PsiClass qualifierClass, @NotNull String staticName, @NotNull PsiImportList importList) throws IncorrectOperationException {
    String qualifiedName  = qualifierClass.getQualifiedName();
    List<PsiJavaCodeReferenceElement> refs = getImportsFromClass(importList, qualifiedName);
    JavaFileCodeStyleFacade javaCodeStyleSettingsFacade = JavaFileCodeStyleFacade.forContext(importList.getContainingFile());
    JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(qualifierClass.getProject());
    boolean hasToBeImportedBySettings =
      (javaCodeStyleSettingsFacade.isToImportOnDemand(qualifiedName) ||
      refs.size() + 1 >= javaCodeStyleSettingsFacade.getNamesCountToUseImportOnDemand());
    if (!hasToBeImportedBySettings ||
        javaCodeStyleManager.hasConflictingOnDemandImport((PsiJavaFile)importList.getContainingFile(), qualifierClass, staticName)) {
      importList.add(JavaPsiFacade.getElementFactory(qualifierClass.getProject()).createImportStaticStatement(qualifierClass, staticName));
    }
    else {
      for (PsiJavaCodeReferenceElement ref : refs) {
        PsiImportStaticStatement importStatement = PsiTreeUtil.getParentOfType(ref, PsiImportStaticStatement.class);
        if (importStatement != null) {
          importStatement.delete();
        }
      }
      importList.add(JavaPsiFacade.getElementFactory(qualifierClass.getProject()).createImportStaticStatement(qualifierClass, "*"));
    }
  }

  /**
   * Retrieves the static import statements from the given import list that reference
   * a specified class.
   *
   * @param importList the list of import statements in a Java file.
   * @param className the fully qualified name of the class for which static imports are to be retrieved.
   * @return a list of static import references corresponding to the specified class.
   */
  public static @NotNull List<PsiJavaCodeReferenceElement> getImportsFromClass(@NotNull PsiImportList importList, String className) {
    List<PsiJavaCodeReferenceElement> array = new ArrayList<>();
    for (PsiImportStaticStatement staticStatement : importList.getImportStaticStatements()) {
      PsiClass psiClass = staticStatement.resolveTargetClass();
      if (psiClass != null && Comparing.strEqual(psiClass.getQualifiedName(), className)) {
        array.add(staticStatement.getImportReference());
      }
    }
    return array;
  }

  @Override
  public void setQualifierExpression(@Nullable PsiExpression newQualifier) throws IncorrectOperationException {
    PsiExpression oldQualifier = getQualifierExpression();
    if (newQualifier == null) {
      if (oldQualifier != null) {
        deleteChildInternal(oldQualifier.getNode());
      }
    }
    else {
      if (oldQualifier == null) {
        CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
        TreeElement dot = (TreeElement)findChildByRole(ChildRole.DOT);
        if (dot == null) {
          dot = Factory.createSingleLeafElement(JavaTokenType.DOT, ".", 0, 1, treeCharTab, getManager());
          dot = addInternal(dot, dot, getFirstChildNode(), Boolean.TRUE);
        }
        addBefore(newQualifier, dot.getPsi());
      }
      getQualifierExpression().replace(newQualifier);
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

    @Override
    public ResolveResult @NotNull [] resolve(@NotNull PsiJavaReference ref, @NotNull PsiFile containingFile, boolean incompleteCode) {
      PsiReferenceExpressionImpl expression = (PsiReferenceExpressionImpl)ref;
      CompositeElement treeParent = expression.getTreeParent();
      IElementType parentType = treeParent == null ? null : treeParent.getElementType();

      List<ResolveResult[]> qualifiers = resolveAllQualifiers(expression, containingFile);
      JavaResolveResult[] result = expression.resolve(parentType, containingFile);

      if (result.length == 0 && incompleteCode && parentType != JavaElementType.REFERENCE_EXPRESSION) {
        result = expression.resolve(JavaElementType.REFERENCE_EXPRESSION, containingFile);
      }

      JavaResolveUtil.substituteResults(expression, result);

      ObjectUtilsRt.reachabilityFence(qualifiers);

      return result;
    }

    private static @NotNull List<ResolveResult[]> resolveAllQualifiers(@NotNull PsiReferenceExpressionImpl expression, @NotNull PsiFile containingFile) {
      // to avoid SOE, resolve all qualifiers starting from the innermost
      PsiElement qualifier = expression.getQualifier();
      if (qualifier == null) return Collections.emptyList();

      List<ResolveResult[]> qualifiers = new SmartList<>();
      ResolveCache resolveCache = ResolveCache.getInstance(containingFile.getProject());
      boolean physical = containingFile.isPhysical();
      qualifier.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
          if (!(expression instanceof PsiReferenceExpressionImpl)) {
            return;
          }
          ResolveResult[] cachedResults = resolveCache.getCachedResults(expression, physical, false, true);
          if (cachedResults != null) {
            return;
          }
          visitElement(expression);
        }

        @Override
        protected void elementFinished(@NotNull PsiElement element) {
          if (!(element instanceof PsiReferenceExpressionImpl)) return;
          PsiReferenceExpressionImpl expression = (PsiReferenceExpressionImpl)element;
          qualifiers.add(resolveCache.resolveWithCaching(expression, INSTANCE, true, false, containingFile));
        }

        // walk only qualifiers, not their arguments and other associated stuff

        @Override
        public void visitExpressionList(@NotNull PsiExpressionList list) { }

        @Override
        public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) { }

        @Override
        public void visitClass(@NotNull PsiClass aClass) { }
      });
      return qualifiers;
    }
  }

  private JavaResolveResult @NotNull [] resolve(IElementType parentType, @NotNull PsiFile containingFile) {
    if (parentType == JavaElementType.REFERENCE_EXPRESSION) {
      JavaResolveResult[] variable = null;
      JavaResolveResult[] result = resolveToVariable(containingFile);
      if (result.length == 1 && result[0].isAccessible()) {
        return result;
      }

      if (result.length > 0) {
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

      if (result.length == 0 && variable == null) {
        result = PsiJavaCodeReferenceElementImpl.tryClassResult(getCachedNormalizedText(), this);
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

  private JavaResolveResult @NotNull [] resolveToMethod(@NotNull PsiFile containingFile) {
    PsiMethodCallExpression methodCall = (PsiMethodCallExpression)getParent();
    MethodResolverProcessor processor = new MethodResolverProcessor(methodCall, containingFile);
    try {
      PsiScopesUtil.setupAndRunProcessor(processor, methodCall, false);
    }
    catch (MethodProcessorSetupFailedException e) {
      return JavaResolveResult.EMPTY_ARRAY;
    }
    return processor.getResult();
  }

  private JavaResolveResult @NotNull [] resolveToPackage(@NotNull PsiFile containingFile) {
    String packageName = getCachedNormalizedText();
    Project project = containingFile.getProject();
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    PsiPackage aPackage = psiFacade.findPackage(packageName);
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

  private JavaResolveResult @NotNull [] resolveToClass(@NotNull PsiElement classNameElement, @NotNull PsiFile containingFile) {
    String className = classNameElement.getText();

    ClassResolverProcessor processor = new ClassResolverProcessor(className, this, containingFile);
    PsiScopesUtil.resolveAndWalk(processor, this, null);
    return processor.getResult();
  }

  private JavaResolveResult @NotNull [] resolveToVariable(@NotNull PsiFile containingFile) {
    VariableResolverProcessor processor = new VariableResolverProcessor(this, containingFile);
    PsiScopesUtil.resolveAndWalk(processor, this, null);
    return processor.getResult();
  }

  @Override
  public JavaResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    return PsiImplUtil.multiResolveImpl(this, incompleteCode, OurGenericsResolver.INSTANCE);
  }

  @Override
  public @NotNull String getCanonicalText() {
    PsiElement element = resolve();
    if (element instanceof PsiClass) {
      String fqn = ((PsiClass)element).getQualifiedName();
      if (fqn != null) return fqn;
    }
    return getCachedNormalizedText();
  }

  private static final Function<PsiReferenceExpressionImpl, PsiType> TYPE_EVALUATOR = new TypeEvaluator();

  private static class TypeEvaluator implements NullableFunction<PsiReferenceExpressionImpl, PsiType> {
    @Override
    public PsiType fun(@NotNull PsiReferenceExpressionImpl expr) {
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
              return PsiTypes.intType();
            }
          }
        }
        return null;
      }

      if (!(resolve instanceof PsiVariable)) return null;
      PsiType type = ((PsiVariable)resolve).getType();
      PsiType ret = type instanceof PsiEllipsisType ? ((PsiEllipsisType)type).toArrayType() : type;
      if (!ret.isValid()) {
        PsiUtil.ensureValidType(ret, "invalid type of " + resolve + " of class " + resolve.getClass() + ", valid=" + resolve.isValid());
      }
      PsiTypeParameterListOwner owner = null;
      if (resolve instanceof PsiField && !((PsiField)resolve).hasModifierProperty(PsiModifier.STATIC)) {
        owner = ((PsiField)resolve).getContainingClass();
      }

      LanguageLevel languageLevel = PsiUtil.getLanguageLevel(file);
      if (ret instanceof PsiClassType) {
        ret = ((PsiClassType)ret).setLanguageLevel(languageLevel);
      }

      if (JavaFeature.GENERICS.isSufficient(languageLevel)) {
        PsiSubstitutor substitutor = result.getSubstitutor();
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
    PsiElement parent = getParent();
    if (parent instanceof PsiMethodCallExpression) {
      return ((PsiMethodCallExpression)parent).getType();
    }
    return JavaResolveCache.getInstance(getProject()).getType(this, TYPE_EVALUATOR);
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
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

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  public void processVariants(@NotNull PsiScopeProcessor processor) {
    DelegatingScopeProcessor filterProcessor = new DelegatingScopeProcessor(processor) {
      private PsiElement myResolveContext;
      private final Set<String> myVarNames = new HashSet<>();

      @Override
      public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
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
        if (element instanceof PsiField) {
          PsiClass parentClass = PsiTreeUtil.getParentOfType(PsiReferenceExpressionImpl.this, PsiClass.class);
          if (!PsiResolveHelper.getInstance(getProject())
            .isAccessible((PsiField)element, PsiReferenceExpressionImpl.this, parentClass)) {
            return true;
          }
        }
        boolean added = myVarNames.add(element.getName());
        return !PsiUtil.isJvmLocalVariable(element) || added;
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

  @Override
  public @NotNull JavaResolveResult advancedResolve(boolean incompleteCode) {
    JavaResolveResult[] results = multiResolve(incompleteCode);
    return results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
  }

  /* see also HighlightMethodUtil.checkStaticInterfaceMethodCallQualifier() */
  private static boolean hasValidQualifier(@NotNull PsiMethod method, @NotNull PsiReferenceExpression ref, PsiElement scope) {
    PsiClass containingClass = method.getContainingClass();
    if (containingClass != null && containingClass.isInterface() && method.hasModifierProperty(PsiModifier.STATIC)) {
      if (!PsiUtil.isAvailable(JavaFeature.STATIC_INTERFACE_CALLS, ref)) {
        return false;
      }

      PsiExpression qualifierExpression = ref.getQualifierExpression();
      if (qualifierExpression == null && (scope instanceof PsiImportStaticStatement || PsiTreeUtil.isAncestor(containingClass, ref, true))) {
        return true;
      }

      if (qualifierExpression instanceof PsiReferenceExpression) {
        PsiElement resolve = ((PsiReferenceExpression)qualifierExpression).resolve();
        if (containingClass.getManager().areElementsEquivalent(resolve, containingClass)) {
          return true;
        }

        if (resolve instanceof PsiTypeParameter) {
          Set<PsiClass> classes = new HashSet<>();
          for (PsiClassType type : ((PsiTypeParameter)resolve).getExtendsListTypes()) {
            PsiClass aClass = type.resolve();
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

  static boolean seemsScrambledByStructure(@NotNull PsiClass aClass) {
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

  @Override
  public PsiType @NotNull [] getTypeParameters() {
    PsiReferenceParameterList parameterList = getParameterList();
    return parameterList != null ? parameterList.getTypeArguments() : PsiType.EMPTY_ARRAY;
  }

  @Override
  public int getTypeParameterCount() {
    PsiReferenceParameterList parameterList = getParameterList();
    return parameterList != null ? parameterList.getTypeArgumentCount() : 0;
  }

  @Override
  public int getTextOffset() {
    ASTNode refName = findChildByRole(ChildRole.REFERENCE_NAME);
    return refName == null ? super.getTextOffset() : refName.getStartOffset();
  }

  @Override
  public @NotNull PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    if (getQualifierExpression() != null) {
      return renameDirectly(newElementName);
    }
    JavaResolveResult resolveResult = advancedResolve(false);
    if (resolveResult.getElement() == null) {
      return renameDirectly(newElementName);
    }
    PsiElement currentFileResolveScope = resolveResult.getCurrentFileResolveScope();
    if (!(currentFileResolveScope instanceof PsiImportStaticStatement) ||
        ((PsiImportStaticStatement)currentFileResolveScope).isOnDemand()) {
      return renameDirectly(newElementName);
    }
    PsiImportStaticStatement importStaticStatement = (PsiImportStaticStatement)currentFileResolveScope;
    String referenceName = importStaticStatement.getReferenceName();
    LOG.assertTrue(referenceName != null);
    PsiElement element = importStaticStatement.getImportReference().resolve();
    if (getManager().areElementsEquivalent(element, resolveResult.getElement())) {
      return renameDirectly(newElementName);
    }
    PsiClass psiClass = importStaticStatement.resolveTargetClass();
    if (psiClass == null) return renameDirectly(newElementName);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
    PsiReferenceExpression expression = (PsiReferenceExpression)factory.createExpressionFromText("X." + newElementName, this);
    PsiReferenceExpression result = (PsiReferenceExpression)replace(expression);
    ((PsiReferenceExpression)result.getQualifierExpression()).bindToElement(psiClass);
    return result;
  }

  private @NotNull PsiElement renameDirectly(@NotNull String newElementName) throws IncorrectOperationException {
    PsiElement oldIdentifier = findChildByRoleAsPsiElement(ChildRole.REFERENCE_NAME);
    if (oldIdentifier == null) {
      throw new IncorrectOperationException();
    }
    String oldRefName = oldIdentifier.getText();
    if (JavaKeywords.THIS.equals(oldRefName) || JavaKeywords.SUPER.equals(oldRefName) || newElementName.equals(oldRefName)) return this;
    PsiIdentifier identifier = JavaPsiFacade.getElementFactory(getProject()).createIdentifier(newElementName);
    oldIdentifier.replace(identifier);
    return this;
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);

    if (isReferenceTo(element)) return this;

    PsiManager manager = getManager();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    PsiJavaParserFacade parserFacade = facade.getParserFacade();
    if (element instanceof PsiClass) {
      boolean preserveQualification = JavaCodeStyleSettingsFacade.getInstance(getProject()).useFQClassNames() && isFullyQualified(this);
      String qName = ((PsiClass)element).getQualifiedName();
      if (qName == null) {
        qName = ((PsiClass)element).getName();
        LOG.assertTrue(qName != null, element);
      }
      else if (JavaPsiFacade.getInstance(manager.getProject()).findClass(qName, getResolveScope()) == null && !preserveQualification) {
        return this;
      }
      else if (facade.getResolveHelper().resolveReferencedClass(qName, this) == null &&
               facade.getResolveHelper().resolveReferencedClass(StringUtil.getPackageName(qName), this) != null) {
        qName = ((PsiClass)element).getName();
        LOG.assertTrue(qName != null, element);
      }
      PsiExpression ref = parserFacade.createExpressionFromText(qName, this);
      getTreeParent().replaceChildInternal(this, (TreeElement)ref.getNode());
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(manager.getProject());
      if (!preserveQualification) {
        ref = (PsiExpression)codeStyleManager.shortenClassReferences(ref, JavaCodeStyleManager.INCOMPLETE_CODE);
      }
      return ref;
    }
    else if (element instanceof PsiPackage) {
      String qName = ((PsiPackage)element).getQualifiedName();
      if (qName.isEmpty()) {
        throw new IncorrectOperationException();
      }
      PsiExpression ref = parserFacade.createExpressionFromText(qName, this);
      getTreeParent().replaceChildInternal(this, (TreeElement)ref.getNode());
      return ref;
    }
    else if ((element instanceof PsiField || element instanceof PsiMethod)) {
      PsiMember member = (PsiMember) element;
      PsiClass psiClass = member.getContainingClass();
      if (psiClass == null) throw new IncorrectOperationException();
      boolean isStatic = ((PsiMember)element).hasModifierProperty(PsiModifier.STATIC);
      String qName = psiClass.getQualifiedName();
      if (qName == null) qName = psiClass.getName(); // local class has no qualified name, but has a short name
      if (qName == null) return this; // ref can't be fixed
      PsiExpression ref = parserFacade.createExpressionFromText(qName + (isStatic ? "." : ".this.") + member.getName(), this);
      getTreeParent().replaceChildInternal(this, (TreeElement)ref.getNode());
      return ref;
    }
    else {
      throw new IncorrectOperationException(element.toString());
    }
  }

  private static boolean isFullyQualified(@NotNull CompositeElement classRef) {
    while (true) {
      ASTNode qualifier = classRef.findChildByRole(ChildRole.QUALIFIER);
      if (qualifier == null) return false;
      if (qualifier.getElementType() != JavaElementType.REFERENCE_EXPRESSION) return false;
      PsiElement refElement = ((PsiReference)qualifier).resolve();
      if (refElement instanceof PsiPackage) return true;
      classRef = (CompositeElement)qualifier;
    }
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (getChildRole(child) == ChildRole.QUALIFIER) {
      ASTNode dot = findChildByType(JavaTokenType.DOT, child);
      assert dot != null : this;
      deleteChildRange(child.getPsi(), dot.getPsi());

      ASTNode first = getFirstChildNode();
      if (getChildRole(first) == ChildRole.REFERENCE_PARAMETER_LIST && first.getFirstChildNode() == null) {
        ASTNode start = first.getTreeNext();
        if (PsiImplUtil.isWhitespaceOrComment(start)) {
          ASTNode next = PsiImplUtil.skipWhitespaceAndComments(start);
          assert next != null : this;
          CodeEditUtil.removeChildren(this, start, next.getTreePrev());
        }
      }
    }
    else if (child.getElementType() == JavaElementType.REFERENCE_PARAMETER_LIST) {
      replaceChildInternal(child, createEmptyRefParameterList(getProject()));
    }
    else {
      super.deleteChildInternal(child);
    }
  }

  public static @NotNull TreeElement createEmptyRefParameterList(@NotNull Project project) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    return (TreeElement)Objects.requireNonNull(factory.createReferenceFromText("foo", null).getParameterList()).getNode();
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));

    switch (role) {
      case ChildRole.REFERENCE_NAME:
        TreeElement lastChild = getLastChildNode();
        return lastChild == null || getChildRole(lastChild) == role ? lastChild : findChildByType(JavaTokenType.IDENTIFIER);

      case ChildRole.QUALIFIER:
        TreeElement firstChild = getFirstChildNode();
        return firstChild != null && getChildRole(firstChild) == ChildRole.QUALIFIER ? firstChild : null;

      case ChildRole.REFERENCE_PARAMETER_LIST:
        return findChildByType(JavaElementType.REFERENCE_PARAMETER_LIST);

      case ChildRole.DOT:
        return findChildByType(JavaTokenType.DOT);
    }

    return null;
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaTokenType.DOT) {
      return ChildRole.DOT;
    }
    if (i == JavaElementType.REFERENCE_PARAMETER_LIST) {
      return ChildRole.REFERENCE_PARAMETER_LIST;
    }
    if (i == JavaTokenType.IDENTIFIER || i == JavaTokenType.THIS_KEYWORD || i == JavaTokenType.SUPER_KEYWORD) {
      return ChildRole.REFERENCE_NAME;
    }
    if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
      return ChildRole.QUALIFIER;
    }
    return ChildRoleBase.NONE;
  }

  @Override
  public PsiReference getReference() {
    return getReferenceNameElement() != null ? this : null;
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
  public @NotNull PsiElement getElement() {
    return this;
  }

  @Override
  public @NotNull TextRange getRangeInElement() {
    return PsiJavaCodeReferenceElementImpl.calcRangeInElement(this);
  }

  @Override
  public PsiElement resolve() {
    return advancedResolve(false).getElement();
  }

  @Override
  public @NotNull String getClassNameText() {
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
  public @NotNull String getQualifiedName() {
    return getCanonicalText();
  }

  private @NotNull String getCachedNormalizedText() {
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