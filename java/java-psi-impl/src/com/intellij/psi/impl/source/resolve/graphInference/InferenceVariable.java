/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.psi.augment.TypeAnnotationModifier;
import com.intellij.psi.impl.light.LightTypeParameter;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;

public class InferenceVariable extends LightTypeParameter {
  private final PsiElement myContext;

  public PsiTypeParameter getParameter() {
    return getDelegate();
  }

  private boolean myThrownBound;
  private final Map<InferenceBound, List<PsiType>> myBounds = new EnumMap<>(InferenceBound.class);
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
      boundType = modifyAnnotations(boundType, (b, modifier) -> modifier.boundAppeared(inferenceVariableType, b));
      variable.addBound(boundType, inferenceBound, session.myIncorporationPhase);
    }
  }

  static PsiType modifyAnnotations(PsiType type, BiFunction<PsiType, TypeAnnotationModifier, TypeAnnotationProvider> executeModifier) {
    for (TypeAnnotationModifier modifier : TypeAnnotationModifier.EP_NAME.getExtensions()) {
      if (type instanceof PsiClassType) {
        final TypeAnnotationProvider annotationProvider = executeModifier.apply(type, modifier);
        if (annotationProvider != null) {
          type = type.annotate(annotationProvider);
        }
      }
    }
    return type;
  }

  boolean addBound(PsiType classType, InferenceBound inferenceBound, @Nullable InferenceIncorporationPhase incorporationPhase) {
    if (PsiUtil.resolveClassInClassTypeOnly(classType) == this) {
      return false;
    }
    List<PsiType> bounds = myBounds.computeIfAbsent(inferenceBound, __ -> new ArrayList<>());

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
                                             Set<InferenceVariable> dependencies,
                                             Set<InferenceVariable> rootDependencies) {
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

  @Nullable
  @Override
  public String getName() {
    return myName;
  }

  PsiElement getCallContext() {
    return myContext;
  }
}
