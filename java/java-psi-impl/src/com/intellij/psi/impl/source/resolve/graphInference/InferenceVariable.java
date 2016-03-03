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

import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightTypeParameter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * User: anna
 */
public class InferenceVariable extends LightTypeParameter {
  private final PsiElement myContext;

  public PsiTypeParameter getParameter() {
    return getDelegate();
  }

  private boolean myThrownBound = false;
  private final Map<InferenceBound, List<PsiType>> myBounds = new HashMap<InferenceBound, List<PsiType>>();
  private final String myName;

  private PsiType myInstantiation = PsiType.NULL;

  InferenceVariable(PsiElement context, PsiTypeParameter parameter, String name) {
    super(parameter);
    myName = name;
    myContext = context;
    TypeConversionUtil.markAsFreshVariable(this, context);
  }

  public PsiType getInstantiation() {
    return myInstantiation;
  }

  public void setInstantiation(PsiType instantiation) {
    myInstantiation = instantiation;
  }

  @NotNull
  @Override
  public PsiClassType[] getExtendsListTypes() {
    final List<PsiClassType> result = new ArrayList<PsiClassType>();
    for (PsiType type : getBounds(InferenceBound.UPPER)) {
      if (type instanceof PsiClassType) {
        result.add((PsiClassType)type);
      }
    }
    return result.toArray(new PsiClassType[result.size()]);
  }

  public boolean addBound(PsiType classType, InferenceBound inferenceBound, @Nullable InferenceIncorporationPhase incorporationPhase) {
    if (inferenceBound == InferenceBound.EQ &&
        PsiUtil.resolveClassInClassTypeOnly(classType) == this) {
      return false;
    }
    List<PsiType> bounds = myBounds.get(inferenceBound);
    if (bounds == null) {
      bounds = new ArrayList<PsiType>();
      myBounds.put(inferenceBound, bounds);
    }

    if (classType == null) {
      classType = PsiType.NULL;
    }

    if (incorporationPhase == null || !bounds.contains(classType)) {
      bounds.add(classType);
      if (incorporationPhase != null) {
        incorporationPhase.addBound(this, classType, inferenceBound);
      }
      return true;
    }
    return false;
  }

  public List<PsiType> getBounds(InferenceBound inferenceBound) {
    final List<PsiType> bounds = myBounds.get(inferenceBound);
    return bounds != null ? new ArrayList<PsiType>(bounds) : Collections.<PsiType>emptyList();
  }

  public List<PsiType> getReadOnlyBounds(InferenceBound inferenceBound) {
    final List<PsiType> bounds = myBounds.get(inferenceBound);
    return bounds != null ? bounds : Collections.<PsiType>emptyList();
  }

  public Set<InferenceVariable> getDependencies(InferenceSession session) {
    final Set<InferenceVariable> dependencies = new LinkedHashSet<InferenceVariable>();
    collectBoundDependencies(session, dependencies);
    collectTransitiveDependencies(session, dependencies, dependencies);
    
    if (!session.hasCapture(this) && dependencies.isEmpty()) {
      return dependencies;
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

  private void collectTransitiveDependencies(InferenceSession session, 
                                             Set<InferenceVariable> dependencies,
                                             Set<InferenceVariable> rootDependencies) {
    final LinkedHashSet<InferenceVariable> newDependencies = new LinkedHashSet<InferenceVariable>();

    for (InferenceVariable dependency : dependencies) {
      dependency.collectBoundDependencies(session, newDependencies);
    }
    newDependencies.removeAll(rootDependencies);
    newDependencies.remove(this);

    if (!newDependencies.isEmpty()) {
      rootDependencies.addAll(newDependencies);
      collectTransitiveDependencies(session, newDependencies, rootDependencies);
    }
  }

  private void collectBoundDependencies(InferenceSession session, Set<InferenceVariable> dependencies) {
    for (Collection<PsiType> boundTypes : myBounds.values()) {
      if (boundTypes != null) {
        for (PsiType bound : boundTypes) {
          session.collectDependencies(bound, dependencies);
        }
      }
    }
  }

  public boolean hasInstantiation(InferenceSession session) {
    List<PsiType> bounds = getBounds(InferenceBound.EQ);
    if (bounds != null) {
      for (PsiType bound : bounds) {
        if (session.isProperType(bound)) return true;
      }
    }
    return false;
  }

  public boolean isThrownBound() {
    return myThrownBound;
  }

  public void setThrownBound() {
    myThrownBound = true;
  }

  @Override
  public boolean isInheritor(@NotNull PsiClass baseClass, boolean checkDeep) {
    for (PsiType type : getBounds(InferenceBound.UPPER)) {
      PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(type);
      if (psiClass != null) {
        if (getManager().areElementsEquivalent(baseClass, psiClass)) return true;
        if (checkDeep && psiClass.isInheritor(baseClass, true)) return true;
      }
    }
    
    return super.isInheritor(baseClass, checkDeep);
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    if (this == another) return true;

    if (getDelegate() == another && myContext != null && !PsiTreeUtil.isAncestor(((PsiTypeParameter)another).getOwner(), myContext, false)) {
      return true;
    }
    return false;
  }

  @Override
  public boolean useDelegateToSubstitute() {
    return false;
  }

  @Override
  public String toString() {
    return getDelegate().toString();
  }

  @Override
  public PsiTypeParameterListOwner getOwner() {
    return null;
  }

  @Nullable
  @Override
  public String getName() {
    return myName;
  }

  public PsiElement getCallContext() {
    return myContext;
  }
}
