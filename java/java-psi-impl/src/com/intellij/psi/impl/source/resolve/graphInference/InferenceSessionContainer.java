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
        final InferenceSession session = PsiResolveHelper.ourGraphGuard.doPreventingRecursion(parent, false,
                                                                         new Computable<InferenceSession>() {
                                                                           @Override
                                                                           public InferenceSession compute() {
                                                                             if (MethodCandidateInfo.isOverloadCheck()) {
                                                                               return startTopLevelInference(parent);
                                                                             }
                                                                             return CachedValuesManager.getCachedValue(parent,
                                                                                                                       new CachedValueProvider<InferenceSession>() {
                                                                                                                         @Nullable
                                                                                                                         @Override
                                                                                                                         public Result<InferenceSession> compute() {
                                                                                                                           return new Result<InferenceSession>(startTopLevelInference(parent), PsiModificationTracker.MODIFICATION_COUNT);
                                                                                                                         }
                                                                                                                       });
                                                                           }
                                                                         });
        if (session != null) {
          final CompoundInitialState compoundInitialState = createState(session);
          final InitialInferenceState initialInferenceState = compoundInitialState.getInitialState(PsiTreeUtil.getParentOfType(argumentList, PsiCall.class));
          if (initialInferenceState != null) {
            return new InferenceSession(initialInferenceState)
              .collectAdditionalAndInfer(parameters, arguments, properties, compoundInitialState.getInitialSubstitutor());
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

  private static InferenceSession startTopLevelInference(@NotNull final PsiElement parent) {
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
        return topLevelSession;
      }
    }

    return null;
  }

  @NotNull
  private static PsiSubstitutor replaceVariables(Collection<InferenceVariable> inferenceVariables) {
    final List<InferenceVariable> targetVars = new ArrayList<InferenceVariable>();
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    final InferenceVariable[] oldVars = inferenceVariables.toArray(new InferenceVariable[inferenceVariables.size()]);
    for (InferenceVariable variable : oldVars) {
      final InferenceVariable newVariable = new InferenceVariable(variable.getCallContext(), variable.getParameter());
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
          var.addBound(substitutor.substitute(bound), boundType);
        }
      }
    }
    return substitutor;
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

    LOG.assertTrue(MethodCandidateInfo.getCurrentMethod(argumentList) == null);
    return top;
  }
}