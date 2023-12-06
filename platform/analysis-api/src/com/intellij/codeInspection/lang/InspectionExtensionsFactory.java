// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.lang;

import com.intellij.codeInspection.HTMLComposer;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An extension point to extend the framework under global inspections.
 */
public abstract class InspectionExtensionsFactory {

  public static final ExtensionPointName<InspectionExtensionsFactory> EP_NAME = ExtensionPointName.create("com.intellij.codeInspection.InspectionExtension");

  public abstract GlobalInspectionContextExtension createGlobalInspectionContextExtension();
  public abstract @Nullable RefManagerExtension createRefManagerExtension(RefManager refManager);
  public abstract @Nullable HTMLComposerExtension createHTMLComposerExtension(final HTMLComposer composer);

  public abstract boolean isToCheckMember(@NotNull PsiElement element, @NotNull String id);

  public abstract @Nullable String getSuppressedInspectionIdsIn(@NotNull PsiElement element);

  /**
   * @return true to allow inspections run locally or false to stop it. The {@param rerunAction}
   * can be used later to restart the same inspections once again (e.g. after the configuration is fixed by a user)
   */
  public boolean isProjectConfiguredToRunInspections(@NotNull Project project,
                                                     boolean online,
                                                     @NotNull Runnable rerunAction) {
    return true;
  }
}
