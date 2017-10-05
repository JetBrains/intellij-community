// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.lang;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefVisitor;
import com.intellij.lang.Language;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface RefManagerExtension<T> {
  @NotNull
  Key<T> getID();

  @NotNull
  Language getLanguage();

  void iterate(@NotNull RefVisitor visitor);

  void cleanup();

  void removeReference(@NotNull RefElement refElement);

  @Nullable
  RefElement createRefElement(PsiElement psiElement);

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
  RefEntity getReference(final String type, final String fqName);

  @Nullable
  String getType(RefEntity entity);

  @NotNull
  RefEntity getRefinedElement(@NotNull RefEntity ref);

  void visitElement(final PsiElement element);

  @Nullable
  String getGroupName(final RefEntity entity);

  boolean belongsToScope(final PsiElement psiElement);

  void export(@NotNull RefEntity refEntity, @NotNull Element element);

  void onEntityInitialized(RefElement refEntity, PsiElement psiElement);
}
