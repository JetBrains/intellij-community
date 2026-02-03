// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff;

import com.intellij.openapi.editor.Editor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface EditorDiffViewer extends FrameDiffTool.DiffViewer {
  default @Nullable Editor getCurrentEditor() {
    return ContainerUtil.getFirstItem(getEditors());
  }

  default @NotNull List<? extends Editor> getHighlightEditors() {
    return getEditors();
  }

  @NotNull List<? extends Editor> getEditors();
}
