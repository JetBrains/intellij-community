// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NonNls public static final String DEFAULT_EXTENSION = "iws";
  @NonNls public static final String DOT_DEFAULT_EXTENSION = "." + DEFAULT_EXTENSION;

  private WorkspaceFileType() {}

  @Override
  @NotNull
  public String getName() {
    return "IDEA_WORKSPACE";
  }

  @Override
  @NotNull
  public String getDescription() {
    return IdeCoreBundle.message("filetype.idea.workspace.description");
  }

  @Nls
  @Override
  public @NotNull String getDisplayName() {
    return IdeCoreBundle.message("filetype.idea.workspace.display.name");
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
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
