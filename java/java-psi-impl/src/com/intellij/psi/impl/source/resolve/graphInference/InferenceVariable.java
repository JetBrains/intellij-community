// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.graphInference;

import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightTypeParameter;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class InferenceVariable extends LightTypeParameter {
  private final PsiElement myContext;

  public PsiTypeParameter getParameter() {
    return getDelegate();
  }

  private boolean myThrownBound;
  private final Map<InferenceBound, List<PsiType>> myBounds = new EnumMap<>(InferenceBound.class);
  private final String myName;

  private PsiType myInstantiation = PsiTypes.nullType();

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

  @Override
  public PsiClassType @NotNull [] getExtendsListTypes() {
    final List<PsiClassType> result = new ArrayList<>();
    for (PsiType type : getBounds(InferenceBound.UPPER)) {
      if (type instanceof PsiClassType) {
        result.add((PsiClassType)type);
      }
    }
    return result.toArray(PsiClassType.EMPTY_ARRAY);
  }

  public static void addBound(PsiType inferenceVariableType, PsiType boundType, InferenceBound inferenceBound, InferenceSession session) {
    final InferenceVariable variable = session.getInferenceVariable(inferenceVariableType);
    if (variable != null) {
      variable.addBound(boundType, inferenceBound, session.myIncorporationPhase);
    }
  }

  boolean addBound(PsiType classType, InferenceBound inferenceBound, @Nullable InferenceIncorporationPhase incorporationPhase) {
    if (PsiUtil.resolveClassInClassTypeOnly(classType) == this) {
      return false;
    }
    List<PsiType> bounds = myBounds.computeIfAbsent(inferenceBound, __ -> new ArrayList<>());

    if (classType == null) {
      classType = PsiTypes.nullType();
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
    return bounds != null ? new ArrayList<>(bounds) : Collections.emptyList();
  }

  public Set<InferenceVariable> getDependencies(InferenceSession session) {
    final Set<InferenceVariable> dependencies = new LinkedHashSet<>();
    collectBoundDependencies(session, dependencies);
    collectTransitiveDependencies(session, dependencies, dependencies);
    
    if (!session.hasCapture(this) && dependencies.isEmpty()) {
      return dependencies;
    }

    if (!session.hasCapture(this)) {
      return dependencies;
    }

    dependencies.removeIf(variable -> !session.hasCapture(variable));
    session.collectCaptureDependencies(this, dependencies);
    return dependencies;
  }

  private void collectTransitiveDependencies(InferenceSession session,
                                             Set<? extends InferenceVariable> dependencies,
                                             Set<? super InferenceVariable> rootDependencies) {
    final LinkedHashSet<InferenceVariable> newDependencies = new LinkedHashSet<>();

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

  private void collectBoundDependencies(InferenceSession session, Set<? super InferenceVariable> dependencies) {
    for (Collection<PsiType> boundTypes : myBounds.values()) {
      if (boundTypes != null) {
        for (PsiType bound : boundTypes) {
          session.collectDependencies(bound, dependencies);
        }
      }
    }
  }

  boolean isThrownBound() {
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
    return this == another;
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

  @Override
  public @Nullable String getName() {
    return myName;
  }

  PsiElement getCallContext() {
    return myContext;
  }
}
