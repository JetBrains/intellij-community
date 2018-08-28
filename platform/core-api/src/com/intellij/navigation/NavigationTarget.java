// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation;

import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface NavigationTarget {

  /**
   * @return {@code true} if it's safe to call other methods of this instance
   */
  boolean isValid();

  /**
   * This method is called only if {@link #isValid()} returns {@code true}.
   */
  @Nullable
  Navigatable getNavigatable();

  /**
   * This method is called only if {@link #isValid()} returns {@code true}.
   */
  @NotNull
  TargetPresentation getTargetPresentation();
}
