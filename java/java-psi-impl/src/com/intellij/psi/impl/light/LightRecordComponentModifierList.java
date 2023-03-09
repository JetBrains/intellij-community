// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.light;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

class LightRecordComponentModifierList extends LightModifierList {
  private final PsiModifierListOwner myParent;
  private final PsiAnnotation.TargetType @NotNull [] myTargets;
  private final PsiRecordComponent myRecordComponent;

  LightRecordComponentModifierList(@NotNull PsiModifierListOwner parent, @NotNull PsiModifierListOwner prototype,
                                   @NotNull PsiRecordComponent component) {
    super(prototype);
    myParent = parent;
    myRecordComponent = component;
    myTargets = AnnotationTargetUtil.getTargetsForLocation(this);
  }
  
  LightRecordComponentModifierList(@NotNull PsiModifierListOwner parent, @NotNull PsiManager manager,
                                   @NotNull PsiRecordComponent component) {
    super(manager);
    myParent = parent;
    myRecordComponent = component;
    myTargets = AnnotationTargetUtil.getTargetsForLocation(this);
  }

  @Override
  public PsiElement getParent() {
    return myParent;
  }

  @Override
  public void setModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException {
    if (hasModifierProperty(name) == value) return;
    super.setModifierProperty(name, value);
  }

  @Override
  public PsiAnnotation @NotNull [] getAnnotations() {
    PsiAnnotation[] annotations = myRecordComponent.getAnnotations();
    if (annotations.length == 0) return annotations;
    return StreamEx.of(annotations).filter(
        anno -> AnnotationTargetUtil.findAnnotationTarget(anno, myTargets) != null)
      .toArray(PsiAnnotation.EMPTY_ARRAY);
  }

  @Override
  public PsiAnnotation findAnnotation(@NotNull String qualifiedName) {
    PsiModifierList list = myRecordComponent.getModifierList();
    if (list == null) return null;
    PsiAnnotation annotation = list.findAnnotation(qualifiedName);
    if (annotation != null && AnnotationTargetUtil.findAnnotationTarget(annotation, myTargets) != null) {
      return annotation;
    }
    return null;
  }

  @Override
  public boolean hasAnnotation(@NotNull String qualifiedName) {
    //noinspection SSBasedInspection
    return findAnnotation(qualifiedName) != null;
  }
}
