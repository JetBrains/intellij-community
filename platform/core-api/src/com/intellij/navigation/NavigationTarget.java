// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation;

import com.intellij.model.Pointer;
import com.intellij.pom.Navigatable;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a minimal entity participating in navigation actions.
 * The entity is expected to be valid if it exists.
 */
@Experimental
public interface NavigationTarget {

  /**
   * @return smart pointer used to restore the instance in the subsequent read actions
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  @NotNull Pointer<? extends NavigationTarget> createPointer();

  /**
   * This method is called once before the actual navigation.
   * In other words it is safe to unstub PSI in the implementation of this method.
   * <p/>
   * This method is called in read action.
   *
   * @return navigatable instance to use when this target is selected
   */
  @NotNull Navigatable getNavigatable();

  /**
   * This method is called if the platform decides to display the target in the UI (e.g., popup).
   * If the target is not displayed in the UI, then only {@link #navigationRequest()} is called.
   * <p/>
   * This method is called in read action.
   *
   * @return presentation to render this target in navigation popup
   */
  @NotNull TargetPresentation getTargetPresentation();

  /**
   * This method is called once before the actual navigation.
   * It is safe to unstub PSI in the implementation of this method.
   *
   * @return a request instance to use when this target is selected,
   * or {@code null} if navigation cannot be performed for any reason
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  default @Nullable NavigationRequest navigationRequest() {
    return getNavigatable().navigationRequest();
  }

  /**
   * Two different symbols may have the same navigation target.
   * #equals and #hashCode are used to filter out same targets to hide them in navigation popup.
   */
  @Override
  boolean equals(Object obj);

  @Override
  int hashCode();
}
