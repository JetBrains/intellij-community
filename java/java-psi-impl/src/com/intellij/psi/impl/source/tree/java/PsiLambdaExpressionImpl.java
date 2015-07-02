/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

public class PsiLambdaExpressionImpl extends ExpressionPsiElement implements PsiLambdaExpression {

  private static final ControlFlowPolicy ourPolicy = new ControlFlowPolicy() {
    @Nullable
    @Override
    public PsiVariable getUsedVariable(@NotNull PsiReferenceExpression refExpr) {
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

  public PsiLambdaExpressionImpl() {
    super(JavaElementType.LAMBDA_EXPRESSION);
  }

  @NotNull
  @Override
  public PsiParameterList getParameterList() {
    return PsiTreeUtil.getRequiredChildOfType(this, PsiParameterList.class);
  }

  @Override
  public int getChildRole(ASTNode child) {
    final IElementType elType = child.getElementType();
    if (elType == JavaTokenType.ARROW) {
      return ChildRole.ARROW;
    } else if (elType == JavaElementType.PARAMETER_LIST) {
      return ChildRole.PARAMETER_LIST;
    } else if (elType == JavaElementType.CODE_BLOCK) {
      return ChildRole.LBRACE;
    } else {
      return ChildRole.EXPRESSION;
    }
  }

  @Override
  public PsiElement getBody() {
    final PsiElement element = getLastChild();
    return element instanceof PsiExpression || element instanceof PsiCodeBlock ? element : null;
  }


  @Nullable
  @Override
  public PsiType getFunctionalInterfaceType() {
    return FunctionalInterfaceParameterizationUtil.getGroundTargetType(LambdaUtil.getFunctionalInterfaceType(this, true), this);
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
    final PsiElement body = getBody();
    if (body instanceof PsiCodeBlock) {
      try {
        ControlFlow controlFlow = ControlFlowFactory.getInstance(getProject()).getControlFlow(body, ourPolicy, false, false);
        int startOffset = controlFlow.getStartOffset(body);
        int endOffset = controlFlow.getEndOffset(body);
        if (startOffset != -1 && endOffset != -1 && ControlFlowUtil.canCompleteNormally(controlFlow, startOffset, endOffset)) {
          return false;
        }
      }
      catch (AnalysisCanceledException e) {
        return false;
      }

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
  public void accept(@NotNull final PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitLambdaExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull final ResolveState state,
                                     final PsiElement lastParent,
                                     @NotNull final PsiElement place) {
    return PsiImplUtil.processDeclarationsInLambda(this, processor, state, lastParent, place);
  }

  @Override
  public String toString() {
    return "PsiLambdaExpression:" + getText();
  }

  @Override
  public boolean hasFormalParameterTypes() {
    final PsiParameter[] parameters = getParameterList().getParameters();
    for (PsiParameter parameter : parameters) {
      if (parameter.getTypeElement() == null) return false;
    }
    return true;
  }

  @Override
  public boolean isAcceptable(PsiType leftType) {
    if (leftType instanceof PsiIntersectionType) {
      for (PsiType conjunctType : ((PsiIntersectionType)leftType).getConjuncts()) {
        if (isAcceptable(conjunctType)) return true;
      }
      return false;
    }
    final PsiExpressionList argsList = PsiTreeUtil.getParentOfType(this, PsiExpressionList.class);

    if (MethodCandidateInfo.ourOverloadGuard.currentStack().contains(argsList)) {
      final MethodCandidateInfo.CurrentCandidateProperties candidateProperties = MethodCandidateInfo.getCurrentMethod(argsList);
      if (candidateProperties != null) {
        final PsiMethod method = candidateProperties.getMethod();
        if (hasFormalParameterTypes() && !InferenceSession.isPertinentToApplicability(this, method)) {
          return true;
        }

        if (LambdaUtil.isPotentiallyCompatibleWithTypeParameter(this, argsList, method)) {
          return true;
        }
      }
    }

    leftType = FunctionalInterfaceParameterizationUtil.getGroundTargetType(leftType, this);
    if (!isPotentiallyCompatible(leftType)) {
      return false;
    }

    if (MethodCandidateInfo.ourOverloadGuard.currentStack().contains(argsList) && !hasFormalParameterTypes()) {
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
          final PsiType lambdaFormalType = toArray(typeElement.getType());
          final PsiType methodParameterType = toArray(parameterTypes[lambdaParamIdx]);
          if (!lambdaFormalType.equals(methodParameterType)) {
            return false;
          }
        }
      }
    }

    PsiType methodReturnType = interfaceMethod.getReturnType();
    if (methodReturnType != null && methodReturnType != PsiType.VOID) {
      Map<PsiElement, PsiType> map = LambdaUtil.getFunctionalTypeMap();
      try {
        if (map.put(this, leftType) != null) {
          return false;
        }
        return LambdaUtil.checkReturnTypeCompatible(this, substitutor.substitute(methodReturnType)) == null;
      }
      finally {
        map.remove(this);
      }
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
    if (methodReturnType == PsiType.VOID) {
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

  private static PsiType toArray(PsiType paramType) {
    if (paramType instanceof PsiEllipsisType) {
      return ((PsiEllipsisType)paramType).toArrayType();
    }
    return paramType;
  }

  @Nullable
  @Override
  public Icon getIcon(int flags) {
    return AllIcons.Nodes.Function;
  }
}
