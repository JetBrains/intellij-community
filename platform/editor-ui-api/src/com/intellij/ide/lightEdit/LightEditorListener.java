// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.EventListener;

@ApiStatus.Experimental
public interface LightEditorListener extends EventListener {
  default void afterCreate(@NotNull LightEditorInfo editorInfo) {}
  default void afterSelect(@Nullable LightEditorInfo editorInfo) {}
  default void beforeClose(@NotNull LightEditorInfo editorInfo) {}
  default void afterClose(@NotNull LightEditorInfo editorInfo) {}
  default void autosaveModeChanged(boolean isAutosave) {}
  default void fileStatusChanged(@NotNull Collection<? extends LightEditorInfo> editorInfos) {}
}
