// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Provides icons for files in project view and editor tabs.
 * The provider is also queried in dumb mode when indices are not available yet.
 * Call {@link DumbService#isDumb()} explicitly do detect such situations.
 *
 * @see com.intellij.openapi.project.DumbService
 */
public interface FileIconProvider {
  ExtensionPointName<FileIconProvider> EP_NAME = new ExtensionPointName<>("com.intellij.fileIconProvider");

  @Nullable
  Icon getIcon(@NotNull VirtualFile file, @Iconable.IconFlags int flags, @Nullable Project project);
}