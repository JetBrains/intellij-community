// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface ModalityStateListener extends EventListener {
  /**
   * @deprecated Use the overload accepting modalEntity
   * {@link #beforeModalityStateChanged(boolean, Object)}
   */
  @Deprecated
  default void beforeModalityStateChanged(boolean entering) {}

  default void beforeModalityStateChanged(boolean entering, @NotNull Object modalEntity) {
    beforeModalityStateChanged(entering);
  }
}
