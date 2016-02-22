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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ExpressionCompatibilityConstraint;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
  public PsiSubstitutor findNestedSubstitutor(PsiElement arg, @Nullable PsiSubstitutor defaultSession) {
    InferenceSession session = myNestedSessions.get(PsiTreeUtil.getParentOfType(arg, PsiCall.class));
    return session == null ? defaultSession : session.getInferenceSubstitution();
  }

  void registerNestedSession(InferenceSession session,
                             PsiType returnType,
                             PsiExpression returnExpression) {
    final PsiSubstitutor callSession = findNestedSubstitutor(((PsiCallExpression)returnExpression).getArgumentList(), null);
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
        final PsiCall topLevelCall = PsiResolveHelper.ourGraphGuard.doPreventingRecursion(parent, false,
                                                                                          new Computable<PsiCall>() {
                                                                                            @Override
                                                                                            public PsiCall compute() {
                                                                                              if (parent instanceof PsiExpression && !PsiPolyExpressionUtil.isPolyExpression((PsiExpression)parent)) {
                                                                                                return null;
                                                                                              }
                                                                                              return treeWalkUp(parent);
                                                                                            }
                                                                                          });
        if (topLevelCall != null) {

          final InferenceSession session;
          if (MethodCandidateInfo.isOverloadCheck() || !PsiDiamondType.ourDiamondGuard.currentStack().isEmpty()) {
            session = startTopLevelInference(topLevelCall);
          }
          else {
            session = CachedValuesManager.getCachedValue(topLevelCall, new CachedValueProvider<InferenceSession>() {
              @Nullable
              @Override
              public Result<InferenceSession> compute() {
                return new Result<InferenceSession>(startTopLevelInference(topLevelCall), PsiModificationTracker.MODIFICATION_COUNT);
              }
            });
          }

          if (session != null) {
            final CompoundInitialState compoundInitialState = createState(session);
            final InitialInferenceState initialInferenceState = compoundInitialState.getInitialState(PsiTreeUtil.getParentOfType(argumentList, PsiCall.class));
            if (initialInferenceState != null) {
              InferenceSession childSession = new InferenceSession(initialInferenceState);
              final List<String> errorMessages = session.getIncompatibleErrorMessages();
              if (errorMessages != null) {
                properties.getInfo().setInferenceError(StringUtil.join(errorMessages, "\n"));
                return childSession.prepareSubstitution();
              }
              return childSession
                .collectAdditionalAndInfer(parameters, arguments, properties, compoundInitialState.getInitialSubstitutor());
            }
          }
          else if (topLevelCall instanceof PsiMethodCallExpression) {
            return new InferenceSession(typeParameters, partialSubstitutor, parent.getManager(), parent).prepareSubstitution();
          }
        }
      }
    }

    final InferenceSession inferenceSession = new InferenceSession(typeParameters, partialSubstitutor, parent.getManager(), parent);
    inferenceSession.initExpressionConstraints(parameters, arguments, parent, null);
    return inferenceSession.infer(parameters, arguments, parent);
  }
  
  private static CompoundInitialState createState(InferenceSession topLevelSession) {
    final PsiSubstitutor topInferenceSubstitutor = replaceVariables(topLevelSession.getInferenceVariables());
    final Map<PsiElement, InitialInferenceState> nestedStates = new LinkedHashMap<PsiElement, InitialInferenceState>();

    final InferenceSessionContainer copy = new InferenceSessionContainer() {
      @Override
      public PsiSubstitutor findNestedSubstitutor(PsiElement arg, @Nullable PsiSubstitutor defaultSession) {
        final InitialInferenceState state = nestedStates.get(PsiTreeUtil.getParentOfType(arg, PsiCall.class));
        if (state != null) {
          return state.getInferenceSubstitutor();
        }
        return super.findNestedSubstitutor(arg, defaultSession);
      }
    };
    final Map<PsiElement, InferenceSession> nestedSessions = topLevelSession.getInferenceSessionContainer().myNestedSessions;
    for (Map.Entry<PsiElement, InferenceSession> entry : nestedSessions.entrySet()) {
      nestedStates.put(entry.getKey(), entry.getValue().createInitialState(copy, topInferenceSubstitutor));
    }

    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    for (InferenceVariable variable : topLevelSession.getInferenceVariables()) {
      final PsiType instantiation = variable.getInstantiation();
      if (instantiation != PsiType.NULL) {
        final PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(topInferenceSubstitutor.substitute(variable));
        if (psiClass instanceof InferenceVariable) {
          substitutor = substitutor.put((PsiTypeParameter)psiClass, instantiation);
        }
      }
    }

    return new CompoundInitialState(substitutor, nestedStates);
  }

  @Nullable
  private static InferenceSession startTopLevelInference(final PsiCall topLevelCall) {
    final JavaResolveResult result = topLevelCall.resolveMethodGenerics();
    if (result instanceof MethodCandidateInfo) {
      final PsiMethod method = ((MethodCandidateInfo)result).getElement();
      final PsiParameter[] topLevelParameters = method.getParameterList().getParameters();
      final PsiExpressionList topLevelCallArgumentList = topLevelCall.getArgumentList();
      LOG.assertTrue(topLevelCallArgumentList != null, topLevelCall);
      final PsiExpression[] topLevelArguments = topLevelCallArgumentList.getExpressions();
      return PsiResolveHelper.ourGraphGuard.doPreventingRecursion(topLevelCall, true, new Computable<InferenceSession>() {
        @Override
        public InferenceSession compute() {
          final InferenceSession topLevelSession =
            new InferenceSession(method.getTypeParameters(), ((MethodCandidateInfo)result).getSiteSubstitutor(), topLevelCall.getManager(), topLevelCall);
          topLevelSession.initExpressionConstraints(topLevelParameters, topLevelArguments, topLevelCall, method, ((MethodCandidateInfo)result).isVarargs());
          topLevelSession.infer(topLevelParameters, topLevelArguments, topLevelCall, ((MethodCandidateInfo)result).createProperties());
          return topLevelSession;
        }
      });
    }
    return null;
  }

  @NotNull
  private static PsiSubstitutor replaceVariables(Collection<InferenceVariable> inferenceVariables) {
    final List<InferenceVariable> targetVars = new ArrayList<InferenceVariable>();
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    final InferenceVariable[] oldVars = inferenceVariables.toArray(new InferenceVariable[inferenceVariables.size()]);
    for (InferenceVariable variable : oldVars) {
      final InferenceVariable newVariable = new InferenceVariable(variable.getCallContext(), variable.getParameter(), variable.getName());
      substitutor = substitutor.put(variable, JavaPsiFacade.getElementFactory(variable.getProject()).createType(newVariable));
      targetVars.add(newVariable);
      if (variable.isThrownBound()) {
        newVariable.setThrownBound();
      }
    }

    for (int i = 0; i < targetVars.size(); i++) {
      InferenceVariable var = targetVars.get(i);
      for (InferenceBound boundType : InferenceBound.values()) {
        for (PsiType bound : oldVars[i].getBounds(boundType)) {
          var.addBound(substitutor.substitute(bound), boundType, null);
        }
      }
    }
    return substitutor;
  }

  @Nullable
  public static PsiCall treeWalkUp(PsiElement context) {
    PsiCall top = null;
    PsiElement parent = PsiTreeUtil.getParentOfType(context, 
                                                    PsiExpressionList.class, 
                                                    PsiLambdaExpression.class,
                                                    PsiConditionalExpression.class,
                                                    PsiCodeBlock.class, 
                                                    PsiCall.class);
    while (true) {
      if (parent instanceof PsiCall) {
        break;
      }

      final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(parent, PsiLambdaExpression.class);
      if (parent instanceof PsiCodeBlock) {
        if (lambdaExpression == null) {
          break;
        }
        else {
          boolean inReturnExpressions = false;
          for (PsiExpression expression : LambdaUtil.getReturnExpressions(lambdaExpression)) {
            inReturnExpressions |= PsiTreeUtil.isAncestor(expression, context, false);
          }

          if (!inReturnExpressions) {
            break;
          }

          if (LambdaUtil.getFunctionalTypeMap().containsKey(lambdaExpression)) {
            break;
          }
        }
      }

      if (parent instanceof PsiConditionalExpression && !PsiPolyExpressionUtil.isPolyExpression((PsiExpression)parent)) {
        break;
      }
      
      if (parent instanceof PsiLambdaExpression && LambdaUtil.getFunctionalTypeMap().containsKey(parent)) {
        break;
      }
      
      final PsiCall psiCall = PsiTreeUtil.getParentOfType(parent, PsiCall.class);
      if (psiCall == null) {
        break;
      }
      final MethodCandidateInfo.CurrentCandidateProperties properties = MethodCandidateInfo.getCurrentMethod(psiCall.getArgumentList());
      if (properties != null) {
        if (properties.isApplicabilityCheck() || 
            lambdaExpression != null && lambdaExpression.hasFormalParameterTypes()) {
          break;
        }
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

    LOG.assertTrue(MethodCandidateInfo.getCurrentMethod(argumentList) == null);
    return top;
  }
}