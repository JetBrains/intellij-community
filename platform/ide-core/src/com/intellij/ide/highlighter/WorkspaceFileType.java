// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.highlighter;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.fileTypes.InternalFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.nio.charset.StandardCharsets;

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
    return IconManager.getInstance().getPlatformIcon(PlatformIcons.IdeaModule);
  }

  @Override
  public boolean isBinary() {
    return false;
  }

  @Override
  public String getCharset(@NotNull VirtualFile file, byte @NotNull [] content) {
    return StandardCharsets.UTF_8.name();
  }
}
