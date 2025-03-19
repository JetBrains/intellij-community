// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInspection.bytecodeAnalysis.ProjectBytecodeAnalysis;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public final class ProtobufInferredAnnotationProvider implements InferredAnnotationProvider {
  @Override
  public @Nullable PsiAnnotation findInferredAnnotation(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
    if (!isProtobufGetter(listOwner)) return null;
    Project project = listOwner.getProject();
    if (project.isDefault()) return null;
    NullableNotNullManager nullableNotNullManager = NullableNotNullManager.getInstance(project);
    if (!nullableNotNullManager.getDefaultNotNull().equals(annotationFQN)) return null;
    return ProjectBytecodeAnalysis.getInstance(project).getNotNullAnnotation();
  }

  private static boolean isProtobufGetter(@NotNull PsiModifierListOwner listOwner) {
    if (!(listOwner instanceof PsiMethod method)) return false;
    if (!method.getName().startsWith("get")) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return false;
    if (method.getReturnType() instanceof PsiPrimitiveType) return false;
    PsiReferenceList extendsList = aClass.getExtendsList();
    if (extendsList == null) return false;
    PsiClassType[] types = extendsList.getReferencedTypes();
    return ContainerUtil.exists(types, t -> TypeUtils.typeEquals("com.google.protobuf.GeneratedMessage", t) ||
                                            TypeUtils.typeEquals("com.google.protobuf.GeneratedMessageV3", t) ||
                                            TypeUtils.typeEquals("com.google.protobuf.GeneratedMessageLite", t));
  }

  @Override
  public @NotNull List<PsiAnnotation> findInferredAnnotations(@NotNull PsiModifierListOwner listOwner) {
    if (!isProtobufGetter(listOwner)) return Collections.emptyList();
    Project project = listOwner.getProject();
    if (project.isDefault()) return Collections.emptyList();
    return Collections.singletonList(ProjectBytecodeAnalysis.getInstance(project).getNotNullAnnotation());
  }
}
