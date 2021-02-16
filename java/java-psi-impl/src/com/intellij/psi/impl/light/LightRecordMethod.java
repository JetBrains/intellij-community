// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.light;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.model.BranchableSyntheticPsiElement;
import com.intellij.model.ModelBranch;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.BitUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import com.intellij.util.VisibilityIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;

public final class LightRecordMethod extends LightMethod implements LightRecordMember, BranchableSyntheticPsiElement {
  private final @NotNull PsiRecordComponent myRecordComponent;

  public LightRecordMethod(@NotNull PsiManager manager,
                           @NotNull PsiMethod method,
                           @NotNull PsiClass containingClass,
                           @NotNull PsiRecordComponent component) {
    super(manager, method, containingClass);
    myRecordComponent = component;
  }

  @Override
  @NotNull
  public PsiRecordComponent getRecordComponent() {
    return myRecordComponent;
  }

  @Override
  public int getTextOffset() {
    return myRecordComponent.getTextOffset();
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return myRecordComponent.getNavigationElement();
  }

  @Override
  public boolean isWritable() {
    return true;
  }

  @Override
  public PsiFile getContainingFile() {
    PsiClass containingClass = getContainingClass();
    return containingClass.getContainingFile();
  }

  @Override
  public PsiType getReturnType() {
    if (DumbService.isDumb(myRecordComponent.getProject())) return myRecordComponent.getType();
    return CachedValuesManager.getCachedValue(this, () -> {
      PsiType type = myRecordComponent.getType()
        .annotate(() -> Arrays.stream(myRecordComponent.getAnnotations())
          .filter(LightRecordMethod::hasTargetApplicableForMethod)
          .toArray(PsiAnnotation[]::new)
        );
      return CachedValueProvider.Result.create(type, this);
    });
  }

  @Override
  public PsiAnnotation @NotNull [] getAnnotations() {
    PsiType returnType = getReturnType();
    if (returnType == null) return PsiAnnotation.EMPTY_ARRAY;
    return returnType.getAnnotations();
  }

  @Override
  public boolean hasAnnotation(@NotNull String fqn) {
    PsiType returnType = getReturnType();
    return returnType != null && returnType.hasAnnotation(fqn);
  }

  @Override
  public @Nullable PsiAnnotation getAnnotation(@NotNull String fqn) {
    PsiType returnType = getReturnType();
    if (returnType == null) return null;
    return returnType.findAnnotation(fqn);
  }

  @Override
  public Icon getElementIcon(final int flags) {
    final RowIcon baseIcon =
      IconManager.getInstance().createLayeredIcon(this, PlatformIcons.METHOD_ICON, ElementPresentationUtil.getFlags(this, false));
    if (BitUtil.isSet(flags, ICON_FLAG_VISIBILITY)) {
      VisibilityIcons.setVisibilityIcon(PsiUtil.ACCESS_LEVEL_PUBLIC, baseIcon);
    }
    return baseIcon;
  }

  @Override
  public @NotNull LightRecordMethod obtainBranchCopy(@NotNull ModelBranch branch) {
    PsiClass recordCopy = branch.obtainPsiCopy(myContainingClass);
    PsiMethod accessorCopy = recordCopy.findMethodBySignature(this, false);
    assert accessorCopy instanceof LightRecordMethod;
    return (LightRecordMethod)accessorCopy;
  }

  @Override
  public @Nullable ModelBranch getModelBranch() {
    return ModelBranch.getPsiBranch(myRecordComponent);
  }

  private static boolean hasTargetApplicableForMethod(PsiAnnotation annotation) {
    return AnnotationTargetUtil.findAnnotationTarget(annotation, PsiAnnotation.TargetType.TYPE_USE, PsiAnnotation.TargetType.METHOD) != null;
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    return myRecordComponent.setName(name);
  }

  @Override
  public PsiElement copy() {
    return myMethod.copy();
  }
}
