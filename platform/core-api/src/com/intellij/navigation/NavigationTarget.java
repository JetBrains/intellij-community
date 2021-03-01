// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation;

import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;

@Experimental
public interface NavigationTarget {

  /**
   * @return {@code true} if it's safe to call other methods of this instance
   */
  boolean isValid();

  /**
   * This method is called once before the actual navigation.
   * In other words it is safe to unstub PSI in the implementation of this method.
   * <p/>
   * This method is called only if {@link #isValid()} returns {@code true}.<br/>
   * This method is called in read action.
   *
   * @return navigatable instance to use when this target is selected
   */
  @NotNull Navigatable getNavigatable();

  /**
   * This method is called if the platform decides to display the target in the UI (e.g., popup).
   * If the target is not displayed in the UI, then only {@link #getNavigatable()} is called.
   * <p/>
   * This method is called only if {@link #isValid()} returns {@code true}.<br/>
   * This method is called in read action.
   *
   * @return presentation to render this target in navigation popup
   */
  @NotNull TargetPresentation getTargetPresentation();

  /**
   * Two different symbols may have the same navigation target.
   * #equals and #hashCode are used to filter out same targets to hide them in navigation popup.
   */
  @Override
  boolean equals(Object obj);

  @Override
  int hashCode();
}
