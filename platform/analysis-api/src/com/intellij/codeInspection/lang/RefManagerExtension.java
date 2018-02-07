// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.lang;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.reference.*;
import com.intellij.lang.Language;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

public interface RefManagerExtension<T> {
  @NotNull
  Key<T> getID();

  @NotNull
  Language getLanguage();

  void iterate(@NotNull RefVisitor visitor);

  void cleanup();

  void removeReference(@NotNull RefElement refElement);

  @Nullable
  RefElement createRefElement(@NotNull PsiElement psiElement);

  /**
   * The method finds problem container (ex: method, class, file) that used to be shown as inspection view tree node.
   * This method will be called if  {@link LocalInspectionTool#getProblemElement(PsiElement)} returns null or PsiFile instance for specific inspection tool.
   *
   * @param psiElement
   * @return container element for given psiElement
   */
  @Nullable
  default PsiNamedElement getElementContainer(@NotNull PsiElement psiElement) {
    return null;
  }

  @Nullable
  RefEntity getReference(String type, String fqName);

  @Nullable
  String getType(@NotNull RefEntity entity);

  @NotNull
  RefEntity getRefinedElement(@NotNull RefEntity ref);

  void visitElement(@NotNull PsiElement element);

  @Nullable
  String getGroupName(@NotNull RefEntity entity);

  boolean belongsToScope(@NotNull PsiElement psiElement);

  void export(@NotNull RefEntity refEntity, @NotNull Element element);

  void onEntityInitialized(@NotNull RefElement refEntity, @NotNull PsiElement psiElement);

  default boolean shouldProcessExternalFile(@NotNull PsiFile file) {
    return false;
  }

  @NotNull
  default Stream<? extends PsiElement> extractExternalFileImplicitReferences(@NotNull PsiFile psiFile) {
    return Stream.empty();
  }
}
