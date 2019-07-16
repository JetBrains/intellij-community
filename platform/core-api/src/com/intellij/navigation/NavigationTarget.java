// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation;

import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Experimental
public interface NavigationTarget {

  /**
   * @return {@code true} if it's safe to call other methods of this instance
   */
  boolean isValid();

  /**
   * This method is called only if {@link #isValid()} returns {@code true}.
   *
   * @return navigatable instance to use when this target is selected
   */
  @Nullable
  Navigatable getNavigatable();

  /**
   * This method is called only if {@link #isValid()} returns {@code true}.
   *
   * @return presentation to render this target in navigation popup
   */
  @NotNull
  TargetPopupPresentation getTargetPresentation();

  /**
   * Two different symbols may have the same navigation target.
   * #equals and #hashCode are used to filter out same targets to hide them in navigation popup.
   */
  @Override
  boolean equals(Object obj);

  @Override
  int hashCode();
}
