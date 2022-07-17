// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @Nullable
  public abstract RefManagerExtension createRefManagerExtension(RefManager refManager);
  @Nullable
  public abstract HTMLComposerExtension createHTMLComposerExtension(final HTMLComposer composer);

  public abstract boolean isToCheckMember(@NotNull PsiElement element, @NotNull String id);

  @Nullable
  public abstract String getSuppressedInspectionIdsIn(@NotNull PsiElement element);

  /**
   * @deprecated use {@link #isProjectConfiguredToRunInspections(Project, boolean, Runnable)}
   */
  @Deprecated(forRemoval = true)
  public boolean isProjectConfiguredToRunInspections(@NotNull Project project, boolean online) {
    return true;
  }

  /**
   * @return true to allow inspections run locally or false to stop it. The {@param rerunAction}
   * can be used later to restart the same inspections once again (e.g. after the configuration is fixed by a user)
   */
  public boolean isProjectConfiguredToRunInspections(@NotNull Project project,
                                                     boolean online,
                                                     @NotNull Runnable rerunAction) {
    return isProjectConfiguredToRunInspections(project, online);
  }
}
