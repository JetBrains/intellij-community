// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.highlighter;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.fileTypes.InternalFileType;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class WorkspaceFileType implements InternalFileType {
  public static final WorkspaceFileType INSTANCE = new WorkspaceFileType();
  public static final @NonNls String DEFAULT_EXTENSION = "iws";
  public static final @NonNls String DOT_DEFAULT_EXTENSION = "." + DEFAULT_EXTENSION;

  private WorkspaceFileType() {}

  @Override
  public @NotNull String getName() {
    return "IDEA_WORKSPACE";
  }

  @Override
  public @NotNull String getDescription() {
    return IdeCoreBundle.message("filetype.idea.workspace.description");
  }

  @Override
  public @Nls @NotNull String getDisplayName() {
    return IdeCoreBundle.message("filetype.idea.workspace.display.name");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.IdeaModule;
  }

  @Override
  public boolean isBinary() {
    return false;
  }

  @Override
  public String getCharset(@NotNull VirtualFile file, final byte @NotNull [] content) {
    return CharsetToolkit.UTF8;
  }
}
