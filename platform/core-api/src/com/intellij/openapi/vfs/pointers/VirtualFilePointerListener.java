// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.pointers;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

public interface VirtualFilePointerListener {
  Topic<VirtualFilePointerListener> TOPIC = Topic.create("VirtualFilePointer", VirtualFilePointerListener.class);

  default void beforeValidityChanged(@NotNull VirtualFilePointer @NotNull [] pointers) {
  }

  default void validityChanged(@NotNull VirtualFilePointer @NotNull [] pointers) {
  }
}
