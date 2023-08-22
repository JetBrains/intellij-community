// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  public static FormatChangedTextUtil getInstance() {
    return ApplicationManager.getApplication().getService(FormatChangedTextUtil.class);
  }

  @SuppressWarnings("unused") // External usages
  public static boolean hasChanges(@NotNull PsiFile file) {
    return VcsFacade.getInstance().hasChanges(file);
  }

  @SuppressWarnings("unused") // External usages
  public boolean isChangeNotTrackedForFile(@NotNull Project project, @NotNull PsiFile file) {
    return VcsFacade.getInstance().isChangeNotTrackedForFile(project, file);
  }

  @SuppressWarnings("unused") // External usages
  @Nullable
  public ChangedRangesInfo getChangedRangesInfo(@NotNull PsiFile file) {
    return VcsFacade.getInstance().getChangedRangesInfo(file);
  }

}
