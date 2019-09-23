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

import java.util.*;

class InitialInferenceState {
  private final List<InferenceVariable> myInferenceVariables;
  private final PsiElement myContext;

  private final PsiSubstitutor myInferenceSubstitutor;
  private final PsiSubstitutor mySiteSubstitutor;
  private final ArrayList<Pair<InferenceVariable[], PsiClassType>> myCaptures;
  private final InferenceSessionContainer myInferenceSessionContainer;
  private final boolean myErased;

  InitialInferenceState(Collection<VariableInfo> inferenceVariables,
                        PsiSubstitutor topInferenceSubstitutor,
                        PsiElement context,
                        PsiSubstitutor inferenceSubstitutor,
                        PsiSubstitutor siteSubstitutor,
                        List<Pair<InferenceVariable[], PsiClassType>> captures,
                        boolean erased, 
                        InferenceSessionContainer inferenceSessionContainer) {
    myErased = erased;
    myInferenceVariables = new ArrayList<>(inferenceVariables.size());
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    PsiSubstitutor subst = PsiSubstitutor.EMPTY;
    for (VariableInfo info : inferenceVariables) {
      if (info.myResolved instanceof InferenceVariable) {
        myInferenceVariables.add((InferenceVariable)info.myResolved);
        if (inferenceSubstitutor.getSubstitutionMap().containsValue(info.myType)) {
          substitutor = substitutor.put(info.myVariable.getParameter(), info.mySubstituted);
          subst = subst.put(info.myVariable, info.mySubstituted);
        }
      }
    }
    myInferenceSubstitutor = substitutor;
    myContext = context;
    mySiteSubstitutor = siteSubstitutor;
    myCaptures = new ArrayList<>();
    for (Pair<InferenceVariable[], PsiClassType> capture : captures) {
      InferenceVariable[] newParameters = new InferenceVariable[capture.first.length];
      InferenceVariable[] parameters = capture.first;
      for (int i = 0; i < parameters.length; i++) {
        final PsiType substitute = topInferenceSubstitutor.substitute(parameters[i]);
        newParameters[i] = (InferenceVariable)PsiUtil.resolveClassInClassTypeOnly(substitute);
      }
      myCaptures.add(Pair.create(newParameters, (PsiClassType)topInferenceSubstitutor.substitute(subst.substitute(capture.second))));
    }
    myInferenceSessionContainer = inferenceSessionContainer;
  }

  InferenceSessionContainer getInferenceSessionContainer() {
    return myInferenceSessionContainer;
  }

  List<InferenceVariable> getInferenceVariables() {
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

  public ArrayList<Pair<InferenceVariable[], PsiClassType>> getCaptures() {
    return myCaptures;
  }

  public boolean isErased() {
    return myErased;
  }

  static class VariableInfo {
    final InferenceVariable myVariable;
    final PsiType mySubstituted;
    final PsiClass myResolved;
    final PsiClassType myType;

    VariableInfo(InferenceVariable variable, PsiSubstitutor substitutor, PsiElementFactory factory) {
      myVariable = variable;
      mySubstituted = substitutor.substitute(variable);
      myResolved = PsiUtil.resolveClassInClassTypeOnly(mySubstituted);
      myType = factory.createType(variable);
    }
  }
}
