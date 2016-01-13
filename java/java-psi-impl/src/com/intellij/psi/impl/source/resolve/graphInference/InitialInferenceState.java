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
import com.intellij.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class InitialInferenceState {
  private final Set<InferenceVariable> myInferenceVariables;
  private final PsiElement myContext;

  private final PsiSubstitutor myInferenceSubstitutor;
  private final PsiSubstitutor mySiteSubstitutor;
  private final List<Pair<PsiTypeParameter[], PsiClassType>> myCaptures;
  private final InferenceSessionContainer myInferenceSessionContainer;

  InitialInferenceState(Set<InferenceVariable> inferenceVariables,
                        PsiSubstitutor topInferenceSubstitutor, 
                        PsiElement context,
                        PsiSubstitutor inferenceSubstitutor,
                        PsiSubstitutor siteSubstitutor,
                        List<Pair<PsiTypeParameter[], PsiClassType>> captures,
                        InferenceSessionContainer inferenceSessionContainer) {
    myInferenceVariables = new HashSet<InferenceVariable>();
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    PsiSubstitutor subst = PsiSubstitutor.EMPTY;
    for (InferenceVariable variable : inferenceVariables) {
      final PsiType substitute = topInferenceSubstitutor.substitute(variable);
      final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(substitute);
      if (aClass instanceof InferenceVariable) {
        myInferenceVariables.add((InferenceVariable)aClass);
        if (inferenceSubstitutor.getSubstitutionMap().containsValue(PsiSubstitutor.EMPTY.substitute(variable))) {
          substitutor = substitutor.put(variable.getParameter(), substitute);
          subst = subst.put(variable, substitute);
        }
      }
    }
    myInferenceSubstitutor = substitutor;
    myContext = context;
    mySiteSubstitutor = siteSubstitutor;
    myCaptures = new ArrayList<Pair<PsiTypeParameter[], PsiClassType>>();
    for (Pair<PsiTypeParameter[], PsiClassType> capture : captures) {
      PsiTypeParameter[] newParameters = new PsiTypeParameter[capture.first.length];
      PsiTypeParameter[] parameters = capture.first;
      for (int i = 0; i < parameters.length; i++) {
        final PsiType substitute = topInferenceSubstitutor.substitute(parameters[i]);
        newParameters[i] = (PsiTypeParameter)PsiUtil.resolveClassInClassTypeOnly(substitute);
      }
      myCaptures.add(Pair.create(newParameters, (PsiClassType)subst.substitute(capture.second)));
    }
    myInferenceSessionContainer = inferenceSessionContainer;
  }

  InferenceSessionContainer getInferenceSessionContainer() {
    return myInferenceSessionContainer;
  }

  Set<InferenceVariable> getInferenceVariables() {
    return myInferenceVariables;
  }

  public PsiElement getContext() {
    return myContext;
  }

  PsiSubstitutor getInferenceSubstitutor() {
    return myInferenceSubstitutor;
  }

  PsiSubstitutor getSiteSubstitutor() {
    return mySiteSubstitutor;
  }

  public List<Pair<PsiTypeParameter[], PsiClassType>> getCaptures() {
    return myCaptures;
  }
}
