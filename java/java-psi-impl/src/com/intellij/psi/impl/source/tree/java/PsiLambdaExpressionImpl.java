// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.FunctionalExpressionStub;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.source.JavaStubPsiElement;
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class PsiLambdaExpressionImpl extends JavaStubPsiElement<FunctionalExpressionStub<PsiLambdaExpression>>
  implements PsiLambdaExpression {

  private static final ControlFlowPolicy ourPolicy = new ControlFlowPolicy() {
    @Override
    public @Nullable PsiVariable getUsedVariable(@NotNull PsiReferenceExpression refExpr) {
      return null;
    }

    @Override
    public boolean isParameterAccepted(@NotNull PsiParameter psiParameter) {
      return true;
    }

    @Override
    public boolean isLocalVariableAccepted(@NotNull PsiLocalVariable psiVariable) {
      return true;
    }
  };

  public PsiLambdaExpressionImpl(@NotNull FunctionalExpressionStub<PsiLambdaExpression> stub) {
    super(stub, JavaStubElementTypes.LAMBDA_EXPRESSION);
  }

  public PsiLambdaExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public @NotNull PsiParameterList getParameterList() {
    return PsiTreeUtil.getRequiredChildOfType(this, PsiParameterList.class);
  }

  @Override
  public PsiElement getBody() {
    final PsiElement element = getLastChild();
    return element instanceof PsiExpression || element instanceof PsiCodeBlock ? element : null;
  }


  @Override
  public @Nullable PsiType getFunctionalInterfaceType() {
    return getGroundTargetType(LambdaUtil.getFunctionalInterfaceType(this, true));
  }

  @Override
  public boolean isVoidCompatible() {
    final PsiElement body = getBody();
    if (body instanceof PsiCodeBlock) {
      for (PsiReturnStatement statement : PsiUtil.findReturnStatements((PsiCodeBlock)body)) {
        if (statement.getReturnValue() != null) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public boolean isValueCompatible() {
    //it could be called when functional type of lambda expression is not yet defined (during lambda expression compatibility constraint reduction)
    //thus inferred results for calls inside could be wrong and should not be cached
    final Boolean result = MethodCandidateInfo.ourOverloadGuard.doPreventingRecursion(this, false, () -> isValueCompatibleNoCache());
    return result != null && result;
  }

  private boolean isValueCompatibleNoCache() {
    final PsiElement body = getBody();
    if (body instanceof PsiCodeBlock) {
      try {
        ControlFlow controlFlow = ControlFlowFactory.getControlFlow(body, ourPolicy, ControlFlowOptions.NO_CONST_EVALUATE);
        int startOffset = controlFlow.getStartOffset(body);
        int endOffset = controlFlow.getEndOffset(body);
        if (startOffset != -1 && endOffset != -1 && ControlFlowUtil.canCompleteNormally(controlFlow, startOffset, endOffset)) {
          return false;
        }
      }
      //error would be shown inside body
      catch (AnalysisCanceledException ignore) {}

      for (PsiReturnStatement statement : PsiUtil.findReturnStatements((PsiCodeBlock)body)) {
        if (statement.getReturnValue() == null) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public PsiType getType() {
    return new PsiLambdaExpressionType(this);
  }

  @Override
  public void accept(final @NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitLambdaExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public boolean processDeclarations(final @NotNull PsiScopeProcessor processor,
                                     final @NotNull ResolveState state,
                                     final PsiElement lastParent,
                                     final @NotNull PsiElement place) {
    return PsiImplUtil.processDeclarationsInLambda(this, processor, state, lastParent, place);
  }

  @Override
  public String toString() {
    return "PsiLambdaExpression";
  }

  @Override
  public boolean hasFormalParameterTypes() {
    final PsiParameter[] parameters = getParameterList().getParameters();
    for (PsiParameter parameter : parameters) {
      PsiTypeElement typeElement = parameter.getTypeElement();
      if (typeElement == null) return false;
      if (typeElement.isInferredType()) return false;
    }
    return true;
  }

  @Override
  public boolean isAcceptable(PsiType leftType, @Nullable PsiMethod method) {
    if (leftType instanceof PsiIntersectionType) {
      for (PsiType conjunctType : ((PsiIntersectionType)leftType).getConjuncts()) {
        if (isAcceptable(conjunctType)) return true;
      }
      return false;
    }
    final PsiExpressionList argsList = PsiTreeUtil.getParentOfType(this, PsiExpressionList.class);

    if (MethodCandidateInfo.isOverloadCheck(argsList) && method != null) {
      if (hasFormalParameterTypes() && !InferenceSession.isPertinentToApplicability(this, method)) {
        return true;
      }

      if (LambdaUtil.isPotentiallyCompatibleWithTypeParameter(this, argsList, method)) {
        return true;
      }
    }

    leftType = getGroundTargetType(leftType);
    if (!isPotentiallyCompatible(leftType)) {
      return false;
    }

    if (MethodCandidateInfo.isOverloadCheck(argsList) && !hasFormalParameterTypes()) {
      return true;
    }

    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(leftType);
    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
    if (interfaceMethod == null) return false;

    if (interfaceMethod.hasTypeParameters()) return false;

    final PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(interfaceMethod, resolveResult);

    if (hasFormalParameterTypes()) {
      final PsiParameter[] lambdaParameters = getParameterList().getParameters();
      final PsiType[] parameterTypes = interfaceMethod.getSignature(substitutor).getParameterTypes();
      for (int lambdaParamIdx = 0, length = lambdaParameters.length; lambdaParamIdx < length; lambdaParamIdx++) {
        PsiParameter parameter = lambdaParameters[lambdaParamIdx];
        final PsiTypeElement typeElement = parameter.getTypeElement();
        if (typeElement != null) {
          final PsiType lambdaFormalType = toArray(parameter.getType());
          final PsiType methodParameterType = toArray(parameterTypes[lambdaParamIdx]);
          if (!lambdaFormalType.equals(methodParameterType)) {
            return false;
          }
        }
      }
    }

    PsiType methodReturnType = interfaceMethod.getReturnType();
    if (methodReturnType != null && !PsiTypes.voidType().equals(methodReturnType)) {
      return LambdaUtil.performWithTargetType(this, leftType, () ->
               LambdaUtil.checkReturnTypeCompatible(this, substitutor.substitute(methodReturnType)) == null);
    }
    return true;
  }

  @Override
  public boolean isPotentiallyCompatible(PsiType left) {
    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(left);
    if (interfaceMethod == null) return false;

    if (getParameterList().getParametersCount() != interfaceMethod.getParameterList().getParametersCount()) {
      return false;
    }
    final PsiType methodReturnType = interfaceMethod.getReturnType();
    final PsiElement body = getBody();
    if (PsiTypes.voidType().equals(methodReturnType)) {
      if (body instanceof PsiCodeBlock) {
        return isVoidCompatible();
      } else {
        return LambdaUtil.isExpressionStatementExpression(body);
      }
    }
    else {
      return body instanceof PsiCodeBlock && isValueCompatible() || body instanceof PsiExpression;
    }
  }

  @Override
  public @Nullable PsiType getGroundTargetType(PsiType functionalInterfaceType) {
    return FunctionalInterfaceParameterizationUtil.getGroundTargetType(functionalInterfaceType, this);
  }

  private static @NotNull PsiType toArray(@NotNull PsiType paramType) {
    if (paramType instanceof PsiEllipsisType) {
      return ((PsiEllipsisType)paramType).toArrayType();
    }
    return paramType;
  }

  @Override
  public @NotNull Icon getIcon(int flags) {
    return IconManager.getInstance().getPlatformIcon(PlatformIcons.Lambda);
  }
}
