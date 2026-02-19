// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class OverrideImplementsAnnotationsHandlerImpl implements OverrideImplementsAnnotationsHandler {
  @Override
  public void transferToTarget(String annotation, PsiModifierListOwner source, PsiModifierListOwner target) {
    Project project = source.getProject();
    NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
    String correctedAnnotation = null;
    if (manager.getNullables().contains(annotation)) {
      correctedAnnotation = manager.getDefaultAnnotation(Nullability.NULLABLE, target);
    }
    else if (manager.getNotNulls().contains(annotation)) {
      correctedAnnotation = manager.getDefaultAnnotation(Nullability.NOT_NULL, target);
    }
    if (correctedAnnotation == null || 
        JavaPsiFacade.getInstance(project).findClass(correctedAnnotation, target.getResolveScope()) == null) {
      correctedAnnotation = annotation;
    }
    OverrideImplementsAnnotationsHandler.super.transferToTarget(correctedAnnotation, source, target);
  }

  @Override
  public String[] getAnnotations(@NotNull PsiFile file) {
    List<String> annotations = getCoreAnnotations(file.getProject());
    annotations.addAll(JavaCodeStyleSettings.getInstance(file).getRepeatAnnotations());
    return ArrayUtilRt.toStringArray(annotations);
  }

  private static @NotNull List<String> getCoreAnnotations(Project project) {
    List<String> annotations = new ArrayList<>();

    NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
    annotations.addAll(manager.getNotNulls());
    annotations.addAll(manager.getNullables());

    annotations.add(AnnotationUtil.NLS);
    return annotations;
  }
}