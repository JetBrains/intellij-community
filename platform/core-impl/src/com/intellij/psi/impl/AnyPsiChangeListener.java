// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

public interface AnyPsiChangeListener {
  default void beforePsiChanged(boolean isPhysical) {
  }

  default void afterPsiChanged(boolean isPhysical) {
  }

  /**
   * @deprecated Use {@link AnyPsiChangeListener} directly.
   */
  @Deprecated
  abstract class Adapter implements AnyPsiChangeListener {
  }
}
