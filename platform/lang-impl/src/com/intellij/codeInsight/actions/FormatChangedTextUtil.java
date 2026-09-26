// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.ChangedRangesInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated use {@link VcsFacade} instead
 */
@Deprecated
public class FormatChangedTextUtil {

  // External usages
  @SuppressWarnings("unused")
  public @Nullable ChangedRangesInfo getChangedRangesInfo(@NotNull PsiFile file) {
    return VcsFacade.getInstance().getChangedRangesInfo(file);
  }

  @SuppressWarnings("unused") // External usages
  public static boolean hasChanges(@NotNull PsiFile file) {
    return VcsFacade.getInstance().hasChanges(file);
  }

  @SuppressWarnings("unused") // External usages
  public boolean isChangeNotTrackedForFile(@NotNull Project project, @NotNull PsiFile file) {
    return VcsFacade.getInstance().isChangeNotTrackedForFile(project, file);
  }

  public static @NotNull FormatChangedTextUtil getInstance() {
    return ApplicationManager.getApplication().getService(FormatChangedTextUtil.class);
  }

}
