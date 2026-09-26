// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.lang;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefVisitor;
import com.intellij.lang.Language;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Stream;


/**
 * An extension to manage the reference graph (see {@link RefManager}).
 */
public interface RefManagerExtension<T> {
  @NotNull
  Key<T> getID();

  default @NotNull @Unmodifiable Collection<Language> getLanguages() {
    return Collections.singleton(getLanguage());
  }

  /**
   * @deprecated override {@link #getLanguages()}
   */
  @Deprecated(forRemoval = true)
  default @NotNull Language getLanguage() {
    throw new UnsupportedOperationException("'getLanguages' method must be overriden in " + getClass());
  }

  void iterate(@NotNull RefVisitor visitor);

  void cleanup();

  void removeReference(@NotNull RefElement refElement);

  @Nullable
  RefElement createRefElement(@NotNull PsiElement psiElement);

  /**
   * The method finds problem container (ex: method, class, file) that used to be shown as inspection view tree node.
   * This method will be called if  {@link LocalInspectionTool#getProblemElement(PsiElement)} returns null or PsiFile instance for specific inspection tool.
   *
   * @return container element for given psiElement
   */
  default @Nullable PsiNamedElement getElementContainer(@NotNull PsiElement psiElement) {
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

  default @NotNull Stream<? extends PsiElement> extractExternalFileImplicitReferences(@NotNull PsiFile psiFile) {
    return Stream.empty();
  }

  default void markExternalReferencesProcessed(@NotNull RefElement file) {

  }
}
