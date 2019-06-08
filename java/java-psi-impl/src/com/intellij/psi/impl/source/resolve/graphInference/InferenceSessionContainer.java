// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.graphInference;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.psi.impl.source.resolve.ParameterTypeInferencePolicy;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ExpressionCompatibilityConstraint;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class InferenceSessionContainer {
  private static final Logger LOG = Logger.getInstance(InferenceSessionContainer.class);
  private final Map<PsiElement, InferenceSession> myNestedSessions = new HashMap<>();

  InferenceSessionContainer() {
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
                             @NotNull PsiType returnType,
                             PsiExpression returnExpression) {
    final PsiSubstitutor callSession = findNestedSubstitutor(((PsiCallExpression)returnExpression).getArgumentList(), null);
    if (callSession == null) {
      final InferenceSession inferenceSession =
        ExpressionCompatibilityConstraint.reduceExpressionCompatibilityConstraint(session, returnExpression, returnType, false);
      if (inferenceSession != null && inferenceSession != session) {
        registerNestedSession(inferenceSession);
        session.propagateVariables(inferenceSession);
      }
    }
  }

  static PsiSubstitutor infer(@NotNull PsiTypeParameter[] typeParameters,
                              @NotNull PsiParameter[] parameters,
                              @NotNull PsiExpression[] arguments,
                              @NotNull PsiSubstitutor partialSubstitutor,
                              @NotNull PsiElement parent,
                              @NotNull ParameterTypeInferencePolicy policy, 
                              @Nullable MethodCandidateInfo currentMethod) {
    final PsiExpressionList argumentList = InferenceSession.getArgumentList(parent);
    if (parent instanceof PsiCall) {
      //overload resolution can't depend on outer call => should not traverse to top
      if (//in order to to avoid caching of candidates's errors on parent (!) , so check for overload resolution is left here
          //But overload resolution can depend on type of lambda parameter. As it can't depend on lambda body,
          //traversing down would stop at lambda level and won't take into account overloaded method
          !MethodCandidateInfo.isOverloadCheck(argumentList)) {
        final PsiCall topLevelCall = PsiResolveHelper.ourGraphGuard.doPreventingRecursion(parent, false,
                                                                                          () -> {
                                                                                            if (parent instanceof PsiExpression && !PsiPolyExpressionUtil.isPolyExpression((PsiExpression)parent)) {
                                                                                              return null;
                                                                                            }
                                                                                            return LambdaUtil.treeWalkUp(parent);
                                                                                          });
        if (topLevelCall != null) {

          InferenceSession session;
          if (MethodCandidateInfo.isOverloadCheck() || !PsiDiamondType.ourDiamondGuard.currentStack().isEmpty() ||
              !policy.equals(DefaultParameterTypeInferencePolicy.INSTANCE)) {
            session = startTopLevelInference(topLevelCall, policy, currentMethod);
          }
          else {
            session = CachedValuesManager.getCachedValue(topLevelCall,
                                                         () -> new CachedValueProvider.Result<>(
                                                           startTopLevelInference(topLevelCall, DefaultParameterTypeInferencePolicy.INSTANCE, null),
                                                           PsiModificationTracker.MODIFICATION_COUNT));

            if (session != null) {
              //reject cached top level session if it was based on wrong candidate: check nested session if candidate (it's type parameters) are the same
              //such situations are avoided when overload resolution is performed (MethodCandidateInfo.isOverloadCheck above)
              //but situations when client code iterates through PsiResolveHelper.getReferencedMethodCandidates or similar are impossible to guess
              final Map<PsiElement, InferenceSession> sessions = session.getInferenceSessionContainer().myNestedSessions;
              final InferenceSession childSession = sessions.get(parent);
              if (childSession != null) {
                for (PsiTypeParameter parameter : typeParameters) {
                  if (!childSession.getInferenceSubstitution().getSubstitutionMap().containsKey(parameter)) {
                    session = startTopLevelInference(topLevelCall, policy, currentMethod);
                    break;
                  }
                }
              }
            }
          }

          if (session != null) {
            final PsiSubstitutor childSubstitutor = inferNested(parameters, arguments, (PsiCall)parent, currentMethod, session);
            if (childSubstitutor != null) return childSubstitutor;
          }
          else if (topLevelCall instanceof PsiMethodCallExpression) {
            return new InferenceSession(typeParameters, partialSubstitutor, parent.getManager(), parent, policy).prepareSubstitution();
          }
        }
      }
    }

    final InferenceSession inferenceSession = new InferenceSession(typeParameters, partialSubstitutor, parent.getManager(), parent, policy);
    inferenceSession.initExpressionConstraints(parameters, arguments, 
                                               currentMethod != null ? currentMethod.getElement() : null, 
                                               currentMethod != null && currentMethod.isVarargs());
    return inferenceSession.infer(parameters, arguments, parent, currentMethod);
  }

  private static PsiSubstitutor inferNested(@NotNull final PsiParameter[] parameters,
                                            @NotNull final PsiExpression[] arguments,
                                            @NotNull final PsiCall parent,
                                            @NotNull final MethodCandidateInfo currentMethod,
                                            @NotNull final InferenceSession parentSession) {
    final List<String> errorMessages = parentSession.getIncompatibleErrorMessages();
    if (errorMessages != null) {
      return null;
    }

    final CompoundInitialState compoundInitialState = createState(parentSession);
    InitialInferenceState initialInferenceState = compoundInitialState.getInitialState(parent);
    if (initialInferenceState != null) {
      InferenceSession childSession = new InferenceSession(initialInferenceState, parentSession.getInferencePolicy());
      return childSession.collectAdditionalAndInfer(parameters, arguments, currentMethod, compoundInitialState.getInitialSubstitutor());
    }

    //we do not investigate lambda return expressions when lambda's return type is already inferred (proper)
    //this way all calls from lambda's return expressions won't appear in nested sessions

    PsiElement gParent = PsiUtil.skipParenthesizedExprUp(parent.getParent());
    //find the nearest parent which appears in the map and start inference with a provided target type for a nested lambda
    while (true) {
      if (gParent instanceof PsiReturnStatement) { //process code block lambda
        final PsiElement returnContainer = gParent.getParent();
        if (returnContainer instanceof PsiCodeBlock) {
          gParent = returnContainer.getParent();
        }
      }
      if (gParent instanceof PsiConditionalExpression) {
        gParent = PsiUtil.skipParenthesizedExprUp(gParent.getParent());
      }
      if (gParent instanceof PsiLambdaExpression) {
        final PsiCall call = PsiTreeUtil.getParentOfType(gParent, PsiCall.class);
        if (call != null) {
          initialInferenceState = compoundInitialState.getInitialState(call);
          if (initialInferenceState != null) {
            final PsiExpressionList argumentList = call.getArgumentList();
            final int idx = argumentList != null ? LambdaUtil.getLambdaIdx(argumentList, gParent) : -1;
            final JavaResolveResult result = PsiDiamondType.getDiamondsAwareResolveResult(call);
            final PsiElement method = result.getElement();
            if (method instanceof PsiMethod && idx > -1) {
              final PsiParameter[] methodParameters = ((PsiMethod)method).getParameterList().getParameters();
              if (methodParameters.length == 0) {
                break;
              }

              //one of the grand parents were found in the top inference session
              //start from it as it is the top level call
              InferenceSession sessionInsideLambda = new InferenceSession(initialInferenceState, parentSession.getInferencePolicy());
              sessionInsideLambda.collectAdditionalAndInfer(methodParameters, argumentList.getExpressions(), ((MethodCandidateInfo)result), compoundInitialState.getInitialSubstitutor());
              return inferNested(parameters, arguments, parent, currentMethod, sessionInsideLambda);
            }
          }
          else {
            gParent = PsiUtil.skipParenthesizedExprUp(call.getParent());
            continue;
          }
        }
      }
      break;
    }
    return null;
  }

  private static CompoundInitialState createState(InferenceSession topLevelSession) {
    final PsiSubstitutor topInferenceSubstitutor = replaceVariables(topLevelSession.getInferenceVariables());
    final Map<PsiElement, InitialInferenceState> nestedStates = new LinkedHashMap<>();

    final InferenceSessionContainer copy = new InferenceSessionContainer() {
      @Override
      public PsiSubstitutor findNestedSubstitutor(PsiElement arg, @Nullable PsiSubstitutor defaultSession) {
        //for the case foo(bar(a -> m())): top level inference won't touch lambda "a -> m()"
        //for the case foo(a -> bar(b -> m())): top level inference would go till nested lambda "b -> m()" and the state from top level could be found here by "bar(b -> m())"
        //but proceeding with additional constraints from saved point would produce new expression constraints with different inference variables (could be found in myNestedSessions)
        //which won't be found in the system if we won't reject stored sessions in such cases
        final PsiSubstitutor substitutor = super.findNestedSubstitutor(arg, null);
        if (substitutor != null) {
          return substitutor;
        }

        final InitialInferenceState state = nestedStates.get(PsiTreeUtil.getParentOfType(arg, PsiCall.class));
        if (state != null) {
          return state.getInferenceSubstitutor();
        }
        return super.findNestedSubstitutor(arg, defaultSession);
      }
    };
    final Map<PsiElement, InferenceSession> nestedSessions = topLevelSession.getInferenceSessionContainer().myNestedSessions;
    for (Map.Entry<PsiElement, InferenceSession> entry : nestedSessions.entrySet()) {
      nestedStates.put(entry.getKey(), entry.getValue().createInitialState(copy, topLevelSession.getInferenceVariables(), topInferenceSubstitutor));
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
  private static InferenceSession startTopLevelInference(final PsiCall topLevelCall,
                                                         final ParameterTypeInferencePolicy policy,
                                                         MethodCandidateInfo currentMethod) {
    final JavaResolveResult result = PsiDiamondType.getDiamondsAwareResolveResult(topLevelCall);
    if (result instanceof MethodCandidateInfo) {
      final PsiMethod method = ((MethodCandidateInfo)result).getElement();
      final PsiParameter[] topLevelParameters = method.getParameterList().getParameters();
      final PsiExpressionList topLevelCallArgumentList = topLevelCall.getArgumentList();
      LOG.assertTrue(topLevelCallArgumentList != null, topLevelCall);
      final PsiExpression[] topLevelArguments = topLevelCallArgumentList.getExpressions();
      return PsiResolveHelper.ourGraphGuard.doPreventingRecursion(topLevelCall, true, () -> {
        final InferenceSession topLevelSession =
          new InferenceSession(method.getTypeParameters(), ((MethodCandidateInfo)result).getSiteSubstitutor(), topLevelCall.getManager(), topLevelCall, policy);
        topLevelSession.setCurrentMethod(currentMethod);
        topLevelSession.initExpressionConstraints(topLevelParameters, topLevelArguments, method, ((MethodCandidateInfo)result).isVarargs());
        topLevelSession.infer(topLevelParameters, topLevelArguments, topLevelCall, ((MethodCandidateInfo)result));
        return topLevelSession;
      });
    }
    return null;
  }

  @NotNull
  private static PsiSubstitutor replaceVariables(Collection<InferenceVariable> inferenceVariables) {
    final List<InferenceVariable> targetVars = new ArrayList<>();
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    final InferenceVariable[] oldVars = inferenceVariables.toArray(new InferenceVariable[0]);
    for (InferenceVariable variable : oldVars) {
      final InferenceVariable newVariable = new InferenceVariable(variable.getCallContext(), variable.getParameter(), variable.getName());
      substitutor = substitutor.put(variable, JavaPsiFacade.getElementFactory(variable.getProject()).createType(newVariable));
      targetVars.add(newVariable);
      if (variable.isThrownBound()) {
        newVariable.setThrownBound();
      }
      newVariable.putUserData(InferenceSession.ORIGINAL_CAPTURE, variable.getUserData(InferenceSession.ORIGINAL_CAPTURE));
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
}