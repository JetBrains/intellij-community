// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.ide;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

public interface FileIconPatcher extends DumbAware {
  ExtensionPointName<FileIconPatcher> EP_NAME = new ExtensionPointName<>("com.intellij.fileIconPatcher");

  @NotNull Icon patchIcon(@NotNull Icon icon,
                          @NotNull VirtualFile file,
                          @Iconable.IconFlags int flags,
                          @Nullable Project project);
}