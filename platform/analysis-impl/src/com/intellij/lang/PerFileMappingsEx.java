// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lang;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public interface PerFileMappingsEx<T> extends PerFileMappings<T> {

  @NotNull
  @Unmodifiable
  Map<VirtualFile, T> getMappings();

  void setMappings(@NotNull Map<VirtualFile, T> mappings);

  @Nullable
  T getDefaultMapping(@Nullable VirtualFile file);
}
