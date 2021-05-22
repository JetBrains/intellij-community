/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInspection.lang;

import com.intellij.codeInspection.HTMLComposer;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
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
