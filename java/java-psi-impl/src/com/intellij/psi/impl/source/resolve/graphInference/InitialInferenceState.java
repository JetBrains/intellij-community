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

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public class InitialInferenceState {
  private final Set<InferenceVariable> myInferenceVariables;
  private final PsiElement myContext;

  private final PsiSubstitutor myInferenceSubstitutor;
  private final PsiSubstitutor mySiteSubstitutor;
  private final List<Pair<PsiTypeParameter[], PsiClassType>> myCaptures;
  private final InferenceSessionContainer myInferenceSessionContainer;

  public InitialInferenceState(Set<InferenceVariable> inferenceVariables,
                               PsiElement context,
                               PsiSubstitutor inferenceSubstitutor,
                               PsiSubstitutor siteSubstitutor,
                               List<Pair<PsiTypeParameter[], PsiClassType>> captures,
                               InferenceSessionContainer inferenceSessionContainer) {
    myInferenceVariables = inferenceVariables;
    myContext = context;
    myInferenceSubstitutor = inferenceSubstitutor;
    mySiteSubstitutor = siteSubstitutor;
    myCaptures = captures;
    myInferenceSessionContainer = inferenceSessionContainer;
  }

  @NotNull
  static PsiSubstitutor copyVariables(List<InferenceVariable> targetVars,
                                      Set<InferenceVariable> inferenceVariables,
                                      PsiElement context) {
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    final InferenceVariable[] oldVars = inferenceVariables.toArray(new InferenceVariable[inferenceVariables.size()]);
    for (InferenceVariable variable : oldVars) {
      final InferenceVariable newVariable = new InferenceVariable(context, variable.getParameter());
      substitutor = substitutor.put(variable, JavaPsiFacade.getElementFactory(variable.getProject()).createType(newVariable));
      targetVars.add(newVariable);
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

  public InferenceSessionContainer getInferenceSessionContainer() {
    return myInferenceSessionContainer;
  }

  public Set<InferenceVariable> getInferenceVariables() {
    return myInferenceVariables;
  }

  public PsiElement getContext() {
    return myContext;
  }

  public PsiSubstitutor getInferenceSubstitutor() {
    return myInferenceSubstitutor;
  }

  public PsiSubstitutor getSiteSubstitutor() {
    return mySiteSubstitutor;
  }

  public List<Pair<PsiTypeParameter[], PsiClassType>> getCaptures() {
    return myCaptures;
  }
}
