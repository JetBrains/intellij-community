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

import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PsiLambdaExpressionImpl extends ExpressionPsiElement implements PsiLambdaExpression {

  public PsiLambdaExpressionImpl() {
    super(JavaElementType.LAMBDA_EXPRESSION);
  }

  @NotNull
  @Override
  public PsiParameterList getParameterList() {
    return PsiTreeUtil.getRequiredChildOfType(this, PsiParameterList.class);
  }

  @Override
  public PsiElement getBody() {
    final PsiElement element = getLastChild();
    return element instanceof PsiExpression || element instanceof PsiCodeBlock ? element : null;
  }

  @Override
  public List<PsiReturnStatement> getReturnStatements() {
    final PsiElement body = getBody();
    final List<PsiReturnStatement> result = new ArrayList<PsiReturnStatement>();
    if (body != null) {
      body.accept(new JavaRecursiveElementVisitor() {
        @Override
        public void visitReturnStatement(PsiReturnStatement statement) {
          result.add(statement);
        }

        @Override
        public void visitClass(PsiClass aClass) {
        }
      });
    }
    return result;
  }

  @Override
  public List<PsiExpression> getReturnExpressions() {
    final PsiElement body = getBody();
    if (body instanceof PsiExpression) {
      //if (((PsiExpression)body).getType() != PsiType.VOID) return Collections.emptyList();
      return Collections.singletonList((PsiExpression)body);
    }
    final List<PsiExpression> result = new ArrayList<PsiExpression>();
    for (PsiReturnStatement returnStatement : getReturnStatements()) {
      final PsiExpression returnValue = returnStatement.getReturnValue();
      if (returnValue != null) {
        result.add(returnValue);
      }
    }
    return result;
  }

  @Nullable
  @Override
  public PsiType getFunctionalInterfaceType() {
    return getFunctionalInterfaceType(this, true);
  }

  @Override
  public boolean isVoidCompatible() {
    final PsiElement body = getBody();
    if (body != null) {
      try {
        ControlFlow controlFlow = ControlFlowFactory.getInstance(getProject()).getControlFlow(body, LocalsOrMyInstanceFieldsControlFlowPolicy
          .getInstance());
        int startOffset = controlFlow.getStartOffset(body);
        int endOffset = controlFlow.getEndOffset(body);
        return startOffset != -1 && endOffset != -1 && !ControlFlowUtil.canCompleteNormally(controlFlow, startOffset, endOffset);
      }
      catch (AnalysisCanceledException e) {
        return true;
      }
    }
    return true;
  }

  @Nullable
  public static PsiType getFunctionalInterfaceType(PsiLambdaExpression expression, final boolean tryToSubstitute) {
    PsiElement parent = expression.getParent();
    while (parent instanceof PsiParenthesizedExpression) {
      parent = parent.getParent();
    }
    PsiType type = null;
    if (parent instanceof PsiTypeCastExpression) {
      type = ((PsiTypeCastExpression)parent).getType();
    }
    else if (parent instanceof PsiVariable) {
      type = ((PsiVariable)parent).getType();
    }
    else if (parent instanceof PsiAssignmentExpression) {
      final PsiExpression lExpression = ((PsiAssignmentExpression)parent).getLExpression();
      type = lExpression.getType();
    }
    else if (parent instanceof PsiExpressionList) {
      final PsiExpressionList expressionList = (PsiExpressionList)parent;
      int lambdaIdx = LambdaUtil.getLambdaIdx(expressionList, expression);
      if (lambdaIdx > -1) {
        if (tryToSubstitute) {
          final PsiElement gParent = expressionList.getParent();
          if (gParent instanceof PsiMethodCallExpression) {
            final PsiMethodCallExpression contextCall = (PsiMethodCallExpression)gParent;
            final JavaResolveResult resolveResult = contextCall.resolveMethodGenerics();
            final PsiElement resolve = resolveResult.getElement();
            if (resolve instanceof PsiMethod) {
              final PsiParameter[] parameters = ((PsiMethod)resolve).getParameterList().getParameters();
              if (lambdaIdx < parameters.length) {
                type = parameters[lambdaIdx].getType();
                final PsiType psiType = type;
                type = PsiResolveHelper.ourGuard.doPreventingRecursion(expression, true, new Computable<PsiType>() {
                  @Override
                  public PsiType compute() {
                    return resolveResult.getSubstitutor().substitute(psiType);
                  }
                });
              }
            }
          }
        } else {
          final Map<PsiElement,PsiMethod> currentMethodCandidates = MethodCandidateInfo.CURRENT_CANDIDATE.get();
          final PsiMethod method = currentMethodCandidates != null ? currentMethodCandidates.get(parent) : null;
          if (method != null) {
            final PsiParameter[] parameters = method.getParameterList().getParameters();
            if (lambdaIdx < parameters.length) {
              type = parameters[lambdaIdx].getType();
            }
          }
        }
      }
    }
    else if (parent instanceof PsiReturnStatement) {
      final PsiMethod method = PsiTreeUtil.getParentOfType(parent, PsiMethod.class);
      if (method != null) {
        type = method.getReturnType();
      }
    }
    else if (parent instanceof PsiLambdaExpression) {
      final PsiType parentInterfaceType = ((PsiLambdaExpression)parent).getFunctionalInterfaceType();
      if (parentInterfaceType != null) {
        type = LambdaUtil.getFunctionalInterfaceReturnType(parentInterfaceType);
      }
    }
    return type;
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

  public static PsiType getLambdaParameterType(PsiParameter param) {
    final PsiElement paramParent = param.getParent();
    if (paramParent instanceof PsiParameterList) {
      final int parameterIndex = ((PsiParameterList)paramParent).getParameterIndex(param);
      if (parameterIndex > -1) {
        final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(param, PsiLambdaExpression.class);
        PsiType type = getFunctionalInterfaceType(lambdaExpression, true);
        if (type == null) {
          type = getFunctionalInterfaceType(lambdaExpression, false);
        }
        final PsiClassType.ClassResolveResult resolveResult = type instanceof PsiClassType ? ((PsiClassType)type).resolveGenerics() : null;
        if (resolveResult != null) {
          final PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(type);
          if (method != null) {
            final PsiParameter[] parameters = method.getParameterList().getParameters();
            if (parameterIndex < parameters.length) {
              final PsiType psiType = resolveResult.getSubstitutor().substitute(parameters[parameterIndex].getType());
              if (!LambdaUtil.dependsOnTypeParams(psiType, lambdaExpression)) {
                if (psiType instanceof PsiWildcardType) {
                  final PsiType bound = ((PsiWildcardType)psiType).getBound();
                  if (bound != null) {
                    return bound;
                  }
                }
                return psiType;
              }
            }
          }
        }
      }
    }
    return new PsiLambdaParameterType(param);
  }
}
