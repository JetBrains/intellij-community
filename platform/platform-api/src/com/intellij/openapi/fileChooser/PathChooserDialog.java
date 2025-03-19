// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface PathChooserDialog {
  DataKey<Boolean> PREFER_LAST_OVER_EXPLICIT = DataKey.create("prefer.last.over.explicit");

  void choose(@Nullable VirtualFile toSelect, @NotNull Consumer<? super List<VirtualFile>> callback);
}
