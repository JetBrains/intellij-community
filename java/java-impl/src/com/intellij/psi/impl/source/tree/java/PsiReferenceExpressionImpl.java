/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.LogUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.filters.AndFilter;
import com.intellij.psi.filters.ConstructorFilter;
import com.intellij.psi.filters.NotFilter;
import com.intellij.psi.filters.OrFilter;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.SourceJavaCodeReference;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.resolve.ClassResolverProcessor;
import com.intellij.psi.impl.source.resolve.JavaResolveCache;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.resolve.VariableResolverProcessor;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.scope.MethodProcessorSetupFailedException;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.scope.processor.MethodResolverProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.*;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class PsiReferenceExpressionImpl extends ExpressionPsiElement implements PsiReferenceExpression, SourceJavaCodeReference {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl");

  private volatile String myCachedQName = null;
  private volatile String myCachedTextSkipWhiteSpaceAndComments = null;

  public PsiReferenceExpressionImpl() {
    super(JavaElementType.REFERENCE_EXPRESSION);
  }

  public PsiExpression getQualifierExpression() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.QUALIFIER);
  }

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
      RefactoringUtil.bindToElementViaStaticImport(qualifierClass, staticName, importList);
    }
    else {
      PsiManagerEx manager = getManager();
      PsiReferenceExpression classRef = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createReferenceExpression(qualifierClass);
      final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
      LeafElement dot = Factory.createSingleLeafElement(JavaTokenType.DOT, ".", 0, 1, treeCharTab, manager);
      addInternal(dot, dot, SourceTreeToPsiMap.psiElementToTree(getParameterList()), Boolean.TRUE);
      addBefore(classRef, SourceTreeToPsiMap.treeElementToPsi(dot));
    }
    return this;
  }

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

  public PsiElement getQualifier() {
    return getQualifierExpression();
  }

  public PsiReference getReference() {
    return this;
  }

  public PsiElement resolve() {
    return advancedResolve(false).getElement();
  }

  public void clearCaches() {
    myCachedQName = null;
    myCachedTextSkipWhiteSpaceAndComments = null;
    super.clearCaches();
  }

  private static final class OurGenericsResolver implements ResolveCache.PolyVariantResolver<PsiJavaReference> {
    public static final OurGenericsResolver INSTANCE = new OurGenericsResolver();

    private static JavaResolveResult[] _resolve(boolean incompleteCode, PsiReferenceExpressionImpl expression) {
      CompositeElement treeParent = expression.getTreeParent();
      IElementType parentType = treeParent != null ? treeParent.getElementType() : null;
      final JavaResolveResult[] result = expression.resolve(parentType);

      if (incompleteCode && parentType != JavaElementType.REFERENCE_EXPRESSION && result.length == 0) {
        return expression.resolve(JavaElementType.REFERENCE_EXPRESSION);
      }
      return result;
    }

    public JavaResolveResult[] resolve(PsiJavaReference ref, boolean incompleteCode) {
      final JavaResolveResult[] result = _resolve(incompleteCode, (PsiReferenceExpressionImpl)ref);
      if (result.length > 0 && result[0].getElement() instanceof PsiClass) {
        final PsiType[] parameters = ((PsiJavaCodeReferenceElement)ref).getTypeParameters();
        final JavaResolveResult[] newResult = new JavaResolveResult[result.length];
        for (int i = 0; i < result.length; i++) {
          final CandidateInfo resolveResult = (CandidateInfo)result[i];
          newResult[i] = new CandidateInfo(resolveResult, resolveResult.getSubstitutor().putAll(
            (PsiClass)resolveResult.getElement(), parameters));
        }
        return newResult;
      }
      return result;
    }
  }

  private JavaResolveResult[] resolve(IElementType parentType) {
    if (parentType == null) {
      parentType = getTreeParent() != null ? getTreeParent().getElementType() : null;
    }
    if (parentType == JavaElementType.REFERENCE_EXPRESSION) {
      JavaResolveResult[] result = resolveToVariable();
      if (result.length > 0) {
        return result;
      }

      final PsiElement classNameElement = getReferenceNameElement();
      if (!(classNameElement instanceof PsiIdentifier)) return JavaResolveResult.EMPTY_ARRAY;
      result = resolveToClass(classNameElement);
      if (result.length > 0) {
        return result;
      }

      return resolveToPackage();
    }
    if (parentType == JavaElementType.METHOD_CALL_EXPRESSION) {
      return resolveToMethod();
    }
    return resolveToVariable();
  }

  private JavaResolveResult[] resolveToMethod() {
    final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)getParent();
    final MethodResolverProcessor processor = new MethodResolverProcessor(methodCall);
    try {
      PsiScopesUtil.setupAndRunProcessor(processor, methodCall, false);
    }
    catch (MethodProcessorSetupFailedException e) {
      return JavaResolveResult.EMPTY_ARRAY;
    }
    return processor.getResult();
  }

  private JavaResolveResult[] resolveToPackage() {
    final String packageName = getCachedTextSkipWhiteSpaceAndComments();
    final PsiManager manager = getManager();
    final PsiPackage aPackage = JavaPsiFacade.getInstance(manager.getProject()).findPackage(packageName);
    if (aPackage == null) {
      return JavaPsiFacade.getInstance(manager.getProject()).isPartOfPackagePrefix(packageName)
             ? CandidateInfo.RESOLVE_RESULT_FOR_PACKAGE_PREFIX_PACKAGE
             : JavaResolveResult.EMPTY_ARRAY;
    }
    return new JavaResolveResult[]{new CandidateInfo(aPackage, PsiSubstitutor.EMPTY)};
  }

  private JavaResolveResult[] resolveToClass(PsiElement classNameElement) {
    final String className = classNameElement.getText();

    final ClassResolverProcessor processor = new ClassResolverProcessor(className, this);
    PsiScopesUtil.resolveAndWalk(processor, this, null);
    return processor.getResult();
  }

  private JavaResolveResult[] resolveToVariable() {
    final VariableResolverProcessor processor = new VariableResolverProcessor(this);
    PsiScopesUtil.resolveAndWalk(processor, this, null);
    return processor.getResult();
  }

  @NotNull
  public JavaResolveResult[] multiResolve(boolean incompleteCode) {
    final PsiManagerEx manager = getManager();
    if (manager == null) {
      LOG.error("getManager() == null!");
      return null;
    }
    ResolveResult[] results = manager.getResolveCache().resolveWithCaching(this, OurGenericsResolver.INSTANCE, true, incompleteCode);
    return (JavaResolveResult[])results;
  }

  @NotNull
  public String getCanonicalText() {
    final PsiElement element = resolve();
    if (element instanceof PsiClass && !(element instanceof PsiTypeParameter)) {
      final String fqn = ((PsiClass)element).getQualifiedName();
      if (fqn != null) return fqn;
      LOG.error("FQN is null. Reference:" + getElement().getText() +
                " resolves to:" + LogUtil.objectAndClass(element) +
                " parent:" + LogUtil.objectAndClass(element.getParent()));
    }
    return getCachedTextSkipWhiteSpaceAndComments();
  }

  public String getQualifiedName() {
    return getCanonicalText();
  }

  public String getReferenceName() {
    PsiElement element = getReferenceNameElement();
    if (element == null) return null;
    return element.getText();
  }

  private final Function<PsiReferenceExpressionImpl, PsiType> ourTypeEvaluator = new TypeEvaluator();

  private static class TypeEvaluator implements NullableFunction<PsiReferenceExpressionImpl, PsiType> {
    public PsiType fun(final PsiReferenceExpressionImpl expr) {
      JavaResolveResult result = expr.advancedResolve(false);
      PsiElement resolve = result.getElement();
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
        if (resolve instanceof PsiField && !((PsiField)resolve).hasModifierProperty(PsiModifier.STATIC)) {
          owner = ((PsiField)resolve).getContainingClass();
        }
      }
      else if (resolve instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)resolve;
        ret = method.getReturnType();
        owner = method;
      }
      if (ret == null) return null;

      final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(expr);
      if (ret instanceof PsiClassType) {
        ret = ((PsiClassType)ret).setLanguageLevel(languageLevel);
      }

      if (languageLevel.isAtLeast(LanguageLevel.JDK_1_5)) {
        final PsiSubstitutor substitutor = result.getSubstitutor();
        if (owner == null || !PsiUtil.isRawSubstitutor(owner, substitutor)) {
          PsiType substitutedType = substitutor.substitute(ret);
          return PsiImplUtil.normalizeWildcardTypeByPosition(substitutedType, expr);
        }
      }

      return TypeConversionUtil.erasure(ret);
    }
  }

  public PsiType getType() {
    return JavaResolveCache.getInstance(getProject()).getType(this, ourTypeEvaluator);
  }

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

    return element.getManager().areElementsEquivalent(element, resolve());
  }


  @NotNull
  public Object[] getVariants() {
    //this reference's variants are rather obtained with processVariants()
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public boolean isSoft() {
    return false;
  }

  public void processVariants(PsiScopeProcessor processor) {
    OrFilter filter = new OrFilter();
    filter.addFilter(ElementClassFilter.CLASS);
    if (isQualified()) {
      filter.addFilter(ElementClassFilter.PACKAGE_FILTER);
    }
    filter.addFilter(new AndFilter(ElementClassFilter.METHOD, new NotFilter(new ConstructorFilter())));
    filter.addFilter(ElementClassFilter.VARIABLE);

    FilterScopeProcessor proc = new FilterScopeProcessor(filter, processor) {
      private final Set<String> myVarNames = new THashSet<String>();

      @Override
      public boolean execute(final PsiElement element, final ResolveState state) {
        if (element instanceof PsiLocalVariable || element instanceof PsiParameter) {
          myVarNames.add(((PsiVariable) element).getName());
        }
        else if (element instanceof PsiField && myVarNames.contains(((PsiVariable) element).getName())) {
          return true;
        }
        else if (element instanceof PsiClass && seemsScrambled((PsiClass)element)) {
          return true;
        }

        return super.execute(element, state);
      }

    };
    PsiScopesUtil.resolveAndWalk(proc, this, null, true);
  }

  private static boolean seemsScrambled(PsiClass element) {
    if (!(element instanceof PsiCompiledElement)) {
      return false;
    }

    final String qualifiedName = element.getQualifiedName();
    return qualifiedName != null &&
           qualifiedName.length() <= 2 &&
           qualifiedName.length() > 0 &&
           Character.isLowerCase(qualifiedName.charAt(0));
  }

  @NotNull
  public JavaResolveResult advancedResolve(boolean incompleteCode) {
    final JavaResolveResult[] results = multiResolve(incompleteCode);
    if (results.length == 1) return results[0];
    return JavaResolveResult.EMPTY;
  }

  public PsiElement getReferenceNameElement() {
    return findChildByRoleAsPsiElement(ChildRole.REFERENCE_NAME);
  }

  public PsiReferenceParameterList getParameterList() {
    return (PsiReferenceParameterList)findChildByRoleAsPsiElement(ChildRole.REFERENCE_PARAMETER_LIST);
  }

  public int getTextOffset() {
    ASTNode refName = findChildByRole(ChildRole.REFERENCE_NAME);
    return refName == null ? super.getTextOffset() : refName.getStartOffset();
  }

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
    if (PsiKeyword.THIS.equals(oldRefName) || PsiKeyword.SUPER.equals(oldRefName)) return this;
    PsiIdentifier identifier = JavaPsiFacade.getInstance(getProject()).getElementFactory().createIdentifier(newElementName);
    oldIdentifier.replace(identifier);
    return this;
  }

  public PsiElement bindToElement(@NotNull final PsiElement element) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);

    if (isReferenceTo(element)) return this;

    final PsiManager manager = getManager();
    final PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(getProject()).getParserFacade();
    if (element instanceof PsiClass) {
      String qName = ((PsiClass)element).getQualifiedName();
      if (qName == null) {
        qName = ((PsiClass)element).getName();
      }
      else if (JavaPsiFacade.getInstance(manager.getProject()).findClass(qName, getResolveScope()) == null) {
        return this;
      }
      final boolean preserveQualification = CodeStyleSettingsManager.getSettings(getProject()).USE_FQ_CLASS_NAMES && isFullyQualified(this);
      PsiExpression ref = parserFacade.createExpressionFromText(qName, this);
      getTreeParent().replaceChildInternal(this, (TreeElement)ref.getNode());
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(manager.getProject());
      if (!preserveQualification) {
        ref = (PsiExpression)codeStyleManager.shortenClassReferences(ref, JavaCodeStyleManager.UNCOMPLETE_CODE);
      }
      return ref;
    }
    else if (element instanceof PsiPackage) {
      final String qName = ((PsiPackage)element).getQualifiedName();
      if (qName.length() == 0) {
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

  public void deleteChildInternal(@NotNull ASTNode child) {
    if (getChildRole(child) == ChildRole.QUALIFIER) {
      ASTNode dot = findChildByRole(ChildRole.DOT);
      super.deleteChildInternal(child);
      deleteChildInternal(dot);
    }
    else {
      super.deleteChildInternal(child);
    }
  }

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

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReferenceExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiReferenceExpression:" + getText();
  }

  public TextRange getRangeInElement() {
    TreeElement nameChild = (TreeElement)findChildByRole(ChildRole.REFERENCE_NAME);
    if (nameChild == null) {
      final TreeElement dot = (TreeElement)findChildByRole(ChildRole.DOT);
      if (dot == null) {
        LOG.error(toString());
      }
      return new TextRange(dot.getStartOffsetInParent() + dot.getTextLength(), getTextLength());
    }
    return new TextRange(nameChild.getStartOffsetInParent(), getTextLength());
  }

  public PsiElement getElement() {
    return this;
  }

  @NotNull
  public PsiType[] getTypeParameters() {
    final PsiReferenceParameterList parameterList = getParameterList();
    if (parameterList == null) return PsiType.EMPTY_ARRAY;
    return parameterList.getTypeArguments();
  }


  public String getClassNameText() {
    String cachedQName = myCachedQName;
    if (cachedQName == null) {
      myCachedQName = cachedQName = PsiNameHelper.getQualifiedClassName(getCachedTextSkipWhiteSpaceAndComments(), false);
    }
    return cachedQName;
  }

  public void fullyQualify(PsiClass targetClass) {
    JavaSourceUtil.fullyQualifyReference(this, targetClass);
  }

  public boolean isQualified() {
    return getChildRole(getFirstChildNode()) == ChildRole.QUALIFIER;
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();

    // We want to reformat method call arguments on method name change because there is a possible situation that they are aligned
    // and method change breaks the alignment.
    // Example:
    //     test(1,
    //          2);
    // Suppose we're renaming the method to test123. We get the following if parameter list is not reformatted:
    //     test123(1,
    //          2);
    PsiElement methodCallCandidate = getParent();
    if (methodCallCandidate instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)methodCallCandidate;
      CodeEditUtil.markToReformat(methodCallExpression.getArgumentList().getNode(), true);
    }
  }

  private String getCachedTextSkipWhiteSpaceAndComments() {
    String whiteSpaceAndComments = myCachedTextSkipWhiteSpaceAndComments;
    if (whiteSpaceAndComments == null) {
      myCachedTextSkipWhiteSpaceAndComments = whiteSpaceAndComments = SourceUtil.getTextSkipWhiteSpaceAndComments(this);
    }
    return whiteSpaceAndComments;
  }
}

