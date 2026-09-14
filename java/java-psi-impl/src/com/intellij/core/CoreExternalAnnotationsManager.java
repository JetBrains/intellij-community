// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.core;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * The external annotations manager of a project without annotation roots. It finds no annotation and writes none.
 *
 * @see JavaCoreProjectEnvironment#createExternalAnnotationsManager
 */
@ApiStatus.Internal
public class CoreExternalAnnotationsManager extends ExternalAnnotationsManager {
  @Override
  public boolean hasAnnotationRootsForFile(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public @Nullable PsiAnnotation findExternalAnnotation(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
    return null;
  }

  @Override
  public @NotNull List<PsiAnnotation> findExternalAnnotations(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
    return Collections.emptyList();
  }

  @Override
  public boolean isExternalAnnotationWritable(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
    return false;
  }

  @Override
  public PsiAnnotation @NotNull [] findExternalAnnotations(@NotNull PsiModifierListOwner listOwner) {
    return PsiAnnotation.EMPTY_ARRAY;
  }

  @Override
  public @Nullable List<PsiAnnotation> findDefaultConstructorExternalAnnotations(@NotNull PsiClass aClass) {
    return null;
  }

  @Override
  public @Nullable List<PsiAnnotation> findDefaultConstructorExternalAnnotations(@NotNull PsiClass aClass, @NotNull String annotationFQN) {
    return null;
  }

  @Override
  public void annotateExternally(@NotNull PsiModifierListOwner listOwner,
                                 @NotNull String annotationFQName,
                                 @NotNull PsiFile fromFile,
                                 PsiNameValuePair @Nullable [] value) {
    throw new UnsupportedOperationException("The core environment has no external annotation roots");
  }

  @Override
  public boolean deannotate(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
    return false;
  }

  @Override
  public boolean editExternalAnnotation(@NotNull PsiModifierListOwner listOwner,
                                        @NotNull String annotationFQN,
                                        PsiNameValuePair @Nullable [] value) {
    return false;
  }

  @Override
  public @NotNull AnnotationPlace chooseAnnotationsPlaceNoUi(@NotNull PsiElement element) {
    return AnnotationPlace.NOWHERE;
  }

  @Override
  public @NotNull AnnotationPlace chooseAnnotationsPlace(@NotNull PsiElement element) {
    return AnnotationPlace.NOWHERE;
  }

  @Override
  public @Nullable List<PsiFile> findExternalAnnotationsFiles(@NotNull PsiModifierListOwner listOwner) {
    return null;
  }

  @Override
  public boolean hasConfiguredAnnotationRoot(@NotNull PsiModifierListOwner owner) {
    return false;
  }
}
