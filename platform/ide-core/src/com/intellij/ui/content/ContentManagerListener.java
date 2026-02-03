// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.content;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface ContentManagerListener extends EventListener {
  default void contentAdded(@NotNull ContentManagerEvent event) {
  }

  default void contentRemoved(@NotNull ContentManagerEvent event) {
  }

  default void contentRemoveQuery(@NotNull ContentManagerEvent event) {
  }

  default void selectionChanged(@NotNull ContentManagerEvent event) {
  }
}