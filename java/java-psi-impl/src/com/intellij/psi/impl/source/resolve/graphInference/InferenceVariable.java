/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;

import java.util.*;

/**
 * User: anna
 */
public class InferenceVariable {
  public PsiTypeParameter getParameter() {
    return myParameter;
  }

  private boolean myThrownBound = false;
  private final Map<InferenceBound, List<PsiType>> myBounds = new HashMap<InferenceBound, List<PsiType>>();
  private final PsiTypeParameter myParameter;

  private PsiType myInstantiation = PsiType.NULL;
  public InferenceVariable(PsiTypeParameter parameter) {
    myParameter = parameter;
  }
  public PsiType getInstantiation() {
    return myInstantiation;
  }

  public void setInstantiation(PsiType instantiation) {
    myInstantiation = instantiation;
  }

  public boolean addBound(PsiType classType, InferenceBound inferenceBound) {
    List<PsiType> list = myBounds.get(inferenceBound);
    if (list == null) {
      list = new ArrayList<PsiType>();
      myBounds.put(inferenceBound, list);
    }
    final int idx = list.indexOf(classType);
    if (idx < 0) {
      list.add(classType);
      return true;
    }
    return false;
  }

  public List<PsiType> getBounds(InferenceBound inferenceBound) {
    final List<PsiType> bounds = myBounds.get(inferenceBound);
    return bounds != null ? new ArrayList<PsiType>(bounds) : Collections.<PsiType>emptyList();
  }

  public Set<InferenceVariable> getDependencies(InferenceSession session) {
    final Set<InferenceVariable> dependencies = new LinkedHashSet<InferenceVariable>();
    for (InferenceBound inferenceBound : InferenceBound.values()) {
      for (PsiType bound : getBounds(inferenceBound)) {
        session.collectDependencies(bound, dependencies);
      }
    }

    next:
    for (InferenceVariable variable : session.getInferenceVariables()) {
      if (!dependencies.contains(variable) && variable != this) {
        for (InferenceBound inferenceBound : InferenceBound.values()) {
          for (PsiType bound : getBounds(inferenceBound)) {
            Set<InferenceVariable> deps = new HashSet<InferenceVariable>();
            session.collectDependencies(bound, deps);
            if (deps.contains(this)) {
              dependencies.add(variable);
              continue next;
            }
          }
        }
      }
    }

    if (!session.hasCapture(this)) {
      return dependencies;
    }

    for (Iterator<InferenceVariable> iterator = dependencies.iterator(); iterator.hasNext(); ) {
      if (!session.hasCapture(iterator.next())) {
        iterator.remove();
      }
    }
    session.collectCaptureDependencies(this, dependencies);
    return dependencies;
  }

  public boolean isThrownBound() {
    return myThrownBound;
  }

  public void setThrownBound() {
    myThrownBound = true;
  }

  public void replaceBounds(InferenceBound boundType, LinkedHashSet<PsiType> bounds) {
    
  }

  @Override
  public String toString() {
    return myParameter.toString();
  }
}
