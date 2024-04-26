// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.HTMLComposer;
import com.intellij.codeInspection.SuppressManager;
import com.intellij.codeInspection.lang.GlobalInspectionContextExtension;
import com.intellij.codeInspection.lang.HTMLComposerExtension;
import com.intellij.codeInspection.lang.InspectionExtensionsFactory;
import com.intellij.codeInspection.lang.RefManagerExtension;
import com.intellij.codeInspection.reference.RefJavaManagerImpl;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaInspectionExtensionsFactory extends InspectionExtensionsFactory {

  @Override
  public GlobalInspectionContextExtension createGlobalInspectionContextExtension() {
    return new GlobalJavaInspectionContextImpl();
  }

  @Override
  public RefManagerExtension createRefManagerExtension(final RefManager refManager) {
    return new RefJavaManagerImpl((RefManagerImpl)refManager);
  }

  @Override
  public HTMLComposerExtension createHTMLComposerExtension(final HTMLComposer composer) {
    return new HTMLJavaHTMLComposerImpl((HTMLComposerImpl)composer);
  }

  @Override
  public boolean isToCheckMember(final @NotNull PsiElement element, final @NotNull String id) {
    return SuppressManager.getInstance().getElementToolSuppressedIn(element, id) == null;
  }

  @Override
  public @Nullable String getSuppressedInspectionIdsIn(final @NotNull PsiElement element) {
    return SuppressManager.getInstance().getSuppressedInspectionIdsIn(element);
  }

  @Override
  public boolean isProjectConfiguredToRunInspections(final @NotNull Project project,
                                                     final boolean online,
                                                     @NotNull Runnable rerunAction) {
    return GlobalJavaInspectionContextImpl.isInspectionsEnabled(online, project, rerunAction);
  }
}