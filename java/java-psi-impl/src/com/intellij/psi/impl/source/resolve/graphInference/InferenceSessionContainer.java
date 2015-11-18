/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.graphInference;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ExpressionCompatibilityConstraint;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class InferenceSessionContainer {
  private static final Logger LOG = Logger.getInstance("#" + InferenceSessionContainer.class.getName());
  private final Map<PsiElement, InferenceSession> myNestedSessions = new HashMap<PsiElement, InferenceSession>();

  public InferenceSessionContainer() {
  }

  public void registerNestedSession(InferenceSession session) {
    myNestedSessions.put(session.getContext(), session);
    myNestedSessions.putAll(session.getInferenceSessionContainer().myNestedSessions);
  }

  @Contract("_, !null -> !null")
  public InferenceSession findNestedCallSession(PsiElement arg, @Nullable InferenceSession defaultSession) {
    InferenceSession session = myNestedSessions.get(PsiTreeUtil.getParentOfType(arg, PsiCall.class));
    return session == null ? defaultSession : session;
  }

  public void registerNestedSession(InferenceSession session,
                                    PsiType returnType,
                                    PsiExpression returnExpression) {
    final InferenceSession callSession = findNestedCallSession(((PsiCallExpression)returnExpression).getArgumentList(), null);
    if (callSession == null) {
      final InferenceSession inferenceSession =
        ExpressionCompatibilityConstraint.reduceExpressionCompatibilityConstraint(session, returnExpression, returnType);
      if (inferenceSession != null && inferenceSession != session) {
        registerNestedSession(inferenceSession);
      }
    }
  }

  static PsiSubstitutor infer(@NotNull PsiTypeParameter[] typeParameters,
                              @NotNull PsiParameter[] parameters,
                              @NotNull PsiExpression[] arguments,
                              @NotNull PsiSubstitutor partialSubstitutor,
                              @NotNull final PsiElement parent) {
    if (parent instanceof PsiCall) {
      final PsiExpressionList argumentList = ((PsiCall)parent).getArgumentList();
      final MethodCandidateInfo.CurrentCandidateProperties properties = MethodCandidateInfo.getCurrentMethod(argumentList);
      if (properties != null && !properties.isApplicabilityCheck()) {
        final Pair<PsiSubstitutor, Map<PsiElement, InitialInferenceState>>
          session = PsiResolveHelper.ourGraphGuard.doPreventingRecursion(parent, false,
                                                                         new Computable<Pair<PsiSubstitutor, Map<PsiElement, InitialInferenceState>>>() {
                                                                                                                      @Override
                                                                                                                      public Pair<PsiSubstitutor, Map<PsiElement, InitialInferenceState>> compute() {
                                                                                                                        return createValue(parent);
                                                                                                                      }
                                                                                                                    });
        if (session != null) {
          final InitialInferenceState initialInferenceState = session.second.get(PsiTreeUtil.getParentOfType(argumentList, PsiCall.class));
          if (initialInferenceState != null) {
           
            return new InferenceSession(initialInferenceState).collectAdditionalAndInfer(parameters, arguments, properties, session.first);
          }
        }
      }
    }

    final InferenceSession inferenceSession = new InferenceSession(typeParameters, partialSubstitutor, parent.getManager(), parent);
    inferenceSession.initExpressionConstraints(parameters, arguments, parent, null);
    return inferenceSession.infer(parameters, arguments, parent);
  }

  private static Pair<PsiSubstitutor, Map<PsiElement, InitialInferenceState>> createValue(@NotNull final PsiElement parent) {
    if (MethodCandidateInfo.isOverloadCheck()) {
      return startTopLevelInference(parent);
    }
    return CachedValuesManager.getCachedValue(parent,
                                              new CachedValueProvider<Pair<PsiSubstitutor, Map<PsiElement, InitialInferenceState>>>() {
                                                @Nullable
                                                @Override
                                                public Result<Pair<PsiSubstitutor, Map<PsiElement, InitialInferenceState>>> compute() {
                                                  return new Result<Pair<PsiSubstitutor, Map<PsiElement, InitialInferenceState>>>(
                                                    startTopLevelInference(parent), PsiModificationTracker.MODIFICATION_COUNT);
                                                }
                                              });
  }

  private static Pair<PsiSubstitutor, Map<PsiElement, InitialInferenceState>> startTopLevelInference(@NotNull final PsiElement parent) {
    final PsiCall topLevelCall = treeWalkUp(parent);
    if (topLevelCall != null) {
      final JavaResolveResult result = topLevelCall.resolveMethodGenerics();
      if (result instanceof MethodCandidateInfo) {
        final PsiMethod method = ((MethodCandidateInfo)result).getElement();
        final PsiParameter[] topLevelParameters = method.getParameterList().getParameters();
        final PsiExpressionList topLevelCallArgumentList = topLevelCall.getArgumentList();
        LOG.assertTrue(topLevelCallArgumentList != null, topLevelCall);
        final PsiExpression[] topLevelArguments = topLevelCallArgumentList.getExpressions();
        final InferenceSession topLevelSession =
          new InferenceSession(method.getTypeParameters(), ((MethodCandidateInfo)result).getSiteSubstitutor(), topLevelCall.getManager(), topLevelCall);
        topLevelSession.initExpressionConstraints(topLevelParameters, topLevelArguments, topLevelCall, method, ((MethodCandidateInfo)result).isVarargs());
        topLevelSession.infer(topLevelParameters, topLevelArguments, topLevelCall, ((MethodCandidateInfo)result).createProperties());

        final Map<PsiElement, InferenceSession> nestedSessions = topLevelSession.getInferenceSessionContainer().myNestedSessions;
        Map<PsiElement, InitialInferenceState> nestedStates = new LinkedHashMap<PsiElement, InitialInferenceState>();
        for (Map.Entry<PsiElement, InferenceSession> entry : nestedSessions.entrySet()) {
          nestedStates.put(entry.getKey(), entry.getValue().createInitialState());
        }

        PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
        for (InferenceVariable variable : topLevelSession.getInferenceVariables()) {
          final PsiType instantiation = variable.getInstantiation();
          if (instantiation != PsiType.NULL) {
            substitutor = substitutor.put(variable, instantiation);
          }
        }
        
        return Pair.create(substitutor, nestedStates);
      }
    }

    return null;
  }

  @Nullable
  private static PsiCall treeWalkUp(PsiElement context) {
    if (context instanceof PsiExpression && !PsiPolyExpressionUtil.isPolyExpression((PsiExpression)context)) {
      return null;
    }
    PsiCall top = null;
    PsiElement parent = PsiTreeUtil.getParentOfType(context, 
                                                    PsiExpressionList.class, 
                                                    PsiLambdaExpression.class, 
                                                    PsiCodeBlock.class, 
                                                    PsiCall.class);
    while (true) {
      if (parent instanceof PsiCall) {
        break;
      }
      if (parent instanceof PsiCodeBlock && PsiTreeUtil.getParentOfType(parent, PsiLambdaExpression.class) == null) {
        break;
      }
      if (parent instanceof PsiLambdaExpression) {
        boolean inReturnExpressions = false;
        for (PsiExpression expression : LambdaUtil.getReturnExpressions((PsiLambdaExpression)parent)) {
          inReturnExpressions |= PsiTreeUtil.isAncestor(expression, context, false);
        }
        if (!inReturnExpressions) {
          break;
        }
      }
      final PsiCall psiCall = PsiTreeUtil.getParentOfType(parent, PsiCall.class);
      if (psiCall == null) {
        break;
      }
      final MethodCandidateInfo.CurrentCandidateProperties properties = MethodCandidateInfo.getCurrentMethod(psiCall.getArgumentList());
      if (properties != null && properties.isApplicabilityCheck()) {
        break;
      }

      top = psiCall;
      if (top instanceof PsiExpression && PsiPolyExpressionUtil.isPolyExpression((PsiExpression)top)) {
        parent = PsiTreeUtil.getParentOfType(parent.getParent(), PsiExpressionList.class, PsiLambdaExpression.class, PsiCodeBlock.class);
      }
      else {
        break;
      }
    }

    if (top == null) {
      return null;
    }

    final PsiExpressionList argumentList = top.getArgumentList();
    if (argumentList == null) {
      return null;
    }

    final MethodCandidateInfo.CurrentCandidateProperties properties = MethodCandidateInfo.getCurrentMethod(argumentList);
    if (properties != null) {
      return null;
    }
    return top;
  }
}