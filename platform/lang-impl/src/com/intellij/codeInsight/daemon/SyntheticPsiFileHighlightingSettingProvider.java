// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.analysis.DefaultHighlightingSettingProvider;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @see SyntheticPsiFileSupport
 */
@ApiStatus.Internal
public class SyntheticPsiFileHighlightingSettingProvider extends DefaultHighlightingSettingProvider {
  @Override
  public @Nullable FileHighlightingSetting getDefaultSetting(@NotNull Project project, @NotNull VirtualFile file) {
    if (!SyntheticPsiFileSupport.isOutsiderFile(file)) return null;
    return FileHighlightingSetting.SKIP_INSPECTION;
  }
}