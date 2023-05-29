// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.navigation;

import com.intellij.model.Pointer;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.OverrideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a minimal entity participating in navigation actions.
 * The entity is expected to be valid if it exists.
 */
@Experimental
@OverrideOnly
public interface NavigationTarget {

  /**
   * @return smart pointer used to restore the instance in the subsequent read actions
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  @NotNull Pointer<? extends NavigationTarget> createPointer();

  /**
   * This method is called if the platform decides to display the target in the UI (e.g., popup).
   * If the target is not displayed in the UI, then only {@link #navigationRequest()} is called.
   * <p/>
   * This method is called in read action.
   *
   * @return presentation to render this target in navigation popup
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  @NotNull TargetPresentation computePresentation();

  /**
   * This method is called once before the actual navigation.
   * It is safe to unstub PSI in the implementation of this method.
   *
   * @return a request instance to use when this target is selected,
   * or {@code null} if navigation cannot be performed for any reason
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  @Nullable NavigationRequest navigationRequest();

  /**
   * Two different symbols may have the same navigation target.
   * #equals and #hashCode are used to filter out same targets to hide them in navigation popup.
   */
  @Override
  boolean equals(Object obj);

  @Override
  int hashCode();
}
