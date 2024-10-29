// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class GeneratedSourcesHighlightingSettingProvider extends DefaultHighlightingSettingProvider {
  @Override
  public @Nullable FileHighlightingSetting getDefaultSetting(@NotNull Project project, @NotNull VirtualFile file) {
    return GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(file, project) ? FileHighlightingSetting.SKIP_INSPECTION : null;
  }
}
