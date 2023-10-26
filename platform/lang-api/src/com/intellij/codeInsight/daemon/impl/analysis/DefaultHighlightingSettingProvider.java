// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation can provide default level of highlighting (one of "none", "syntax checks", "inspections") for a file.
 * User can override this level for a file via Hector-the-inspector component.
 * If implementation returns {@code null}, next one is checked. If nobody returns anything, "Inspections" level will be used
 * Implement {@link com.intellij.openapi.project.DumbAware} interface to allow implementation to be called in dumb mode
 */
public abstract class DefaultHighlightingSettingProvider {
  public static final ExtensionPointName<DefaultHighlightingSettingProvider> EP_NAME =
    new ExtensionPointName<>("com.intellij.defaultHighlightingSettingProvider");

  public abstract @Nullable FileHighlightingSetting getDefaultSetting(@NotNull Project project, @NotNull VirtualFile file);
}
