// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.FunctionalExpressionStub;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.source.JavaStubPsiElement;
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.conflictResolvers.DuplicateConflictResolver;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.*;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public final class PsiMethodReferenceExpressionImpl extends JavaStubPsiElement<FunctionalExpressionStub<PsiMethodReferenceExpression>>
  implements PsiMethodReferenceExpression {
  private static final Logger LOG = Logger.getInstance(PsiMethodReferenceExpressionImpl.class);
  private static final MethodReferenceResolver RESOLVER = new MethodReferenceResolver();

  public PsiMethodReferenceExpressionImpl(@NotNull FunctionalExpressionStub<PsiMethodReferenceExpression> stub) {
    super(stub, JavaStubElementTypes.METHOD_REF_EXPRESSION);
  }

  public PsiMethodReferenceExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public PsiTypeElement getQualifierType() {
    final PsiElement qualifier = getQualifier();
    return qualifier instanceof PsiTypeElement ? (PsiTypeElement)qualifier : null;
  }

  @Override
  public @Nullable PsiType getFunctionalInterfaceType() {
    return getGroundTargetType(LambdaUtil.getFunctionalInterfaceType(this, true));
  }

  @Override
  public boolean isExact() {
    return getPotentiallyApplicableMember() != null;
  }

  @Override
  public boolean isPotentiallyCompatible(@Nullable PsiType functionalInterfaceType) {
    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
    if (interfaceMethod == null) return false;

    final MethodReferenceResolver resolver = new MethodReferenceResolver() {
      @Override
      protected PsiConflictResolver createResolver(@NotNull PsiMethodReferenceExpressionImpl referenceExpression,
                                                   @NotNull PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult,
                                                   PsiMethod interfaceMethod,
                                                   MethodSignature signature) {
        return DuplicateConflictResolver.INSTANCE;
      }
    };

    ResolveResult[] result = LambdaUtil.performWithTargetType(this, functionalInterfaceType, () ->
      resolver.resolve(this, getContainingFile(), false));

    final PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult = PsiMethodReferenceUtil.getQualifierResolveResult(this);
    final int interfaceArity = interfaceMethod.getParameterList().getParametersCount();
    for (ResolveResult resolveResult : result) {
      final PsiElement element = resolveResult.getElement();
      if (element instanceof PsiMethod) {
        final boolean isStatic = ((PsiMethod)element).hasModifierProperty(PsiModifier.STATIC);
        final int parametersCount = ((PsiMethod)element).getParameterList().getParametersCount();
        if (qualifierResolveResult.isReferenceTypeQualified() && getReferenceNameElement() instanceof PsiIdentifier) {
          int offset = isStatic ? 0 : 1;
          if (parametersCount == interfaceArity - offset) {
            return true;
          }
          if (((PsiMethod)element).isVarArgs() && (interfaceMethod.isVarArgs() || interfaceArity >= parametersCount + offset - 1)) {
            return true;
          }
        }
        else if (!isStatic) {
          if (parametersCount == interfaceArity) {
            return true;
          }
          if (((PsiMethod)element).isVarArgs() && (interfaceMethod.isVarArgs() || interfaceArity >= parametersCount - 1)) {
            return true;
          }
        }
      } else if (element instanceof PsiClass) {
        return true;
      }
    }
    return false;
  }

  @Override
  public @Nullable PsiType getGroundTargetType(PsiType functionalInterfaceType) {
    return FunctionalInterfaceParameterizationUtil.getGroundTargetType(functionalInterfaceType);
  }

  @Override
  public PsiMember getPotentiallyApplicableMember() {
    return CachedValuesManager.getCachedValue(this, () -> CachedValueProvider.Result
      .create(getPotentiallyApplicableMemberInternal(), PsiModificationTracker.MODIFICATION_COUNT, this));
  }

  private PsiMember getPotentiallyApplicableMemberInternal() {
    final PsiElement element = getReferenceNameElement();
    final PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult = PsiMethodReferenceUtil.getQualifierResolveResult(this);
    final PsiClass containingClass = qualifierResolveResult.getContainingClass();
    if (containingClass == null) return null;

    //If the method reference expression has the form ReferenceType::[TypeArguments]Identifier or ClassType::[TypeArguments]new,
    //then ReferenceType does not denote a raw type.
    final PsiElement qualifier = getQualifier();
    if (qualifier instanceof PsiReferenceExpression) {
      PsiElement resolve = ((PsiReferenceExpression)qualifier).resolve();
      if (resolve instanceof PsiClass && ((PsiClass)resolve).hasTypeParameters()) {
        return null;
      }
    }

    PsiMethod[] methods = null;
    if (element instanceof PsiIdentifier) {
      final String identifierName = element.getText();
      // findMethodsByName is supposed to be faster than getVisibleSignatures
      methods = containingClass.findMethodsByName(identifierName, true);
      if (methods.length == 0) return null;
      if (methods.length > 1) {
        final List<PsiMethod> result = new ArrayList<>();
        for (HierarchicalMethodSignature signature : containingClass.getVisibleSignatures()) {
          if (identifierName.equals(signature.getName())) {
            result.add(signature.getMethod());
          }
        }

        if (result.isEmpty()) {
          return null;
        }
        methods = result.toArray(PsiMethod.EMPTY_ARRAY);
      }
    }
    else if (isConstructor()) {
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
      if (factory.isArrayClass(containingClass)) {
        final PsiTypeParameter[] typeParameters = containingClass.getTypeParameters();
        if (typeParameters.length != 1) return null;
        final PsiType componentType = qualifierResolveResult.getSubstitutor().substitute(typeParameters[0]);
        LOG.assertTrue(componentType != null, qualifierResolveResult.getSubstitutor());
        //15.13.1 A method reference expression of the form ArrayType :: new is always exact.
        return factory.createMethodFromText("public " + componentType.createArrayType().getCanonicalText() + " __array__(int i) {return null;}", this);
      }
      else {
        methods = containingClass.getConstructors();
      }
    }

    if (methods != null) {
      PsiMethod psiMethod = null;
      if (methods.length > 0) {
        //The type to search has exactly one member method with the name Identifier/constructor that is accessible to the class or interface
        // in which the method reference expression appears
        for (PsiMethod method : methods) {
          if (PsiUtil.isAccessible(method, this, null)) {
            if (psiMethod != null) return null;
            psiMethod = method;
          }
        }
        if (psiMethod == null) return null;
        // not variable arity
        if (psiMethod.isVarArgs()) return null;

        //If this method/constructor is generic (p8.4.4), then the method reference expression provides TypeArguments.
        if (psiMethod.getTypeParameters().length > 0) {
          final PsiReferenceParameterList parameterList = getParameterList();
          return parameterList != null && parameterList.getTypeParameterElements().length > 0 ? psiMethod : null;
        }
      }
      return psiMethod == null ? containingClass : psiMethod;
    }
    return null;
  }

  @Override
  public PsiExpression getQualifierExpression() {
    final PsiElement qualifier = getQualifier();
    return qualifier instanceof PsiExpression ? (PsiExpression)qualifier : null;
  }

  @Override
  public PsiType getType() {
    return new PsiMethodReferenceType(this);
  }

  @Override
  public PsiElement getReferenceNameElement() {
    final PsiElement element = getLastChild();
    return element instanceof PsiIdentifier || PsiUtil.isJavaToken(element, JavaTokenType.NEW_KEYWORD) ? element : null;
  }

  @Override
  public void processVariants(final @NotNull PsiScopeProcessor processor) {
    final FilterScopeProcessor proc = new FilterScopeProcessor(ElementClassFilter.METHOD, processor);
    PsiScopesUtil.resolveAndWalk(proc, this, null, true);
  }

  @Override
  public void setQualifierExpression(@Nullable PsiExpression newQualifier) throws IncorrectOperationException {
    if (newQualifier == null) {
      LOG.error("Forbidden null qualifier");
      return;
    }
    final PsiExpression expression = getQualifierExpression();
    if (expression != null) {
      expression.replace(newQualifier);
    } else {
      final PsiElement qualifier = getQualifier();
      if (qualifier != null) {
        qualifier.replace(newQualifier);
      }
    }
  }

  @Override
  public JavaResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    return PsiImplUtil.multiResolveImpl(this, incompleteCode, RESOLVER);
  }

  @Override
  public PsiElement getQualifier() {
    final PsiElement element = getFirstChild();
    return element instanceof PsiExpression || element instanceof PsiTypeElement ? element : null;
  }

  @Override
  public @NotNull TextRange getRangeInElement() {
    final PsiElement element = getReferenceNameElement();
    if (element != null) {
      final int offsetInParent = element.getStartOffsetInParent();
      return new TextRange(offsetInParent, offsetInParent + element.getTextLength());
    }
    final PsiElement colons = findChildByType(JavaTokenType.DOUBLE_COLON);
    if (colons != null) {
      final int offsetInParent = colons.getStartOffsetInParent();
      return new TextRange(offsetInParent, offsetInParent + colons.getTextLength());
    }
    LOG.error(getText());
    return null;
  }

  @Override
  public @NotNull String getCanonicalText() {
    return getText();
  }

  @Override
  public boolean isReferenceTo(final @NotNull PsiElement element) {
    if (!(element instanceof PsiMethod)) return false;
    final PsiMethod method = (PsiMethod)element;

    final PsiElement nameElement = getReferenceNameElement();
    if (nameElement instanceof PsiIdentifier) {
      if (!nameElement.getText().equals(method.getName())) return false;
    }
    else if (PsiUtil.isJavaToken(nameElement, JavaTokenType.NEW_KEYWORD)) {
      if (!method.isConstructor()) return false;
    }

    return element.getManager().areElementsEquivalent(element, resolve());
  }

  @Override
  public void accept(final @NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitMethodReferenceExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);

    if (isReferenceTo(element) || !isPhysical()) return this;
    if (element instanceof PsiMethod) {
      return handleElementRename(((PsiMethod)element).getName());
    }
    else if (element instanceof PsiClass) {
      return this;
    }
    else {
      throw new IncorrectOperationException(element.toString());
    }
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    if (isConstructor()) return this;
    PsiElement oldIdentifier = getReferenceNameElement();
    if (oldIdentifier == null) {
      throw new IncorrectOperationException();
    }
    PsiIdentifier identifier = JavaPsiFacade.getElementFactory(getProject()).createIdentifier(newElementName);
    oldIdentifier.replace(identifier);
    return this;
  }

  @Override
  public boolean isConstructor() {
    final PsiElement element = getReferenceNameElement();
    return element instanceof PsiKeyword && JavaKeywords.NEW.equals(element.getText());
  }

  @Override
  public String toString() {
    return "PsiMethodReferenceExpression";
  }

  @Override
  public boolean isAcceptable(PsiType left, PsiMethod method) {
    if (left instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)left).getConjuncts()) {
        if (isAcceptable(conjunct)) return true;
      }
      return false;
    }

    final PsiExpressionList argsList = PsiTreeUtil.getParentOfType(this, PsiExpressionList.class);
    final boolean isExact = isExact();
    if (method != null && MethodCandidateInfo.isOverloadCheck(argsList)) {
      if (isExact && !InferenceSession.isPertinentToApplicability(this, method)) {
        return true;
      }

      if (LambdaUtil.isPotentiallyCompatibleWithTypeParameter(this, argsList, method)) {
        return true;
      }
    }

    left = getGroundTargetType(left);
    if (!isPotentiallyCompatible(left)) {
      return false;
    }

    if (MethodCandidateInfo.isOverloadCheck(argsList)) {
      if (!isExact) {
        return true;
      }
    }

     // A method reference is congruent with a function type if the following are true:
     //   The function type identifies a single compile-time declaration corresponding to the reference.
     //   One of the following is true:
     //      i)The return type of the function type is void.
     //     ii)The return type of the function type is R;
     //        the result of applying capture conversion (5.1.10) to the return type of the invocation type (15.12.2.6) of the chosen declaration is R',
     //        where R is the target type that may be used to infer R'; neither R nor R' is void; and R' is compatible with R in an assignment context.

    JavaResolveResult result = LambdaUtil.performWithTargetType(this, left, () -> advancedResolve(false));

    if (result instanceof MethodCandidateInfo && !((MethodCandidateInfo)result).isApplicable()) {
      return false;
    }

    final PsiElement resolve = result.getElement();
    if (resolve == null) {
      return false;
    }

    return PsiMethodReferenceUtil.isReturnTypeCompatible(this, result, left);
  }

  @Override
  public @Nullable Icon getIcon(int flags) {
    return IconManager.getInstance().getPlatformIcon(PlatformIcons.MethodReference);
  }

  @Override
  public PsiElement bindToElementViaStaticImport(final @NotNull PsiClass qualifierClass) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public @NotNull PsiElement getElement() {
    return this;
  }

  @Override
  public PsiElement resolve() {
    return advancedResolve(false).getElement();
  }

  @Override
  public Object @NotNull [] getVariants() {
    // this reference's variants are rather obtained with processVariants()
    return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  public PsiReference getReference() {
    return this;
  }

  @Override
  public @NotNull JavaResolveResult advancedResolve(boolean incompleteCode) {
    final JavaResolveResult[] results = multiResolve(incompleteCode);
    return results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
  }

  @Override
  public String getReferenceName() {
    final PsiElement element = getReferenceNameElement();
    return element != null ? element.getText() : null;
  }

  @Override
  public PsiReferenceParameterList getParameterList() {
    return PsiTreeUtil.getChildOfType(this, PsiReferenceParameterList.class);
  }

  @Override
  public PsiType @NotNull [] getTypeParameters() {
    final PsiReferenceParameterList parameterList = getParameterList();
    return parameterList != null ? parameterList.getTypeArguments() : PsiType.EMPTY_ARRAY;
  }

  @Override
  public boolean isQualified() {
    return getQualifier() != null;
  }

  @Override
  public String getQualifiedName() {
    return getCanonicalText();
  }

}
