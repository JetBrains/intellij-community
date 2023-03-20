// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.update;

public interface Activatable {
  default void showNotify() {
  }

  default void hideNotify() {
  }

  /**
   * @deprecated Use {@link Activatable} directly.
   */
  @Deprecated
  class Adapter implements Activatable {
  }
}
