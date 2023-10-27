// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public interface SelectInTarget {
  @ApiStatus.Internal
  ProjectExtensionPointName<SelectInTarget> EP_NAME = new ProjectExtensionPointName<>("com.intellij.selectInTarget");

  @Override
  @Nls String toString();

  /**
   * This should be called in an read action
   */
  boolean canSelect(SelectInContext context);

  void selectIn(SelectInContext context, final boolean requestFocus);

  /** Tool window this target is supposed to select in */
  default @Nullable @NonNls String getToolWindowId() {
    return null;
  }

  /** aux view id specific for tool window, e.g. Project/Packages/J2EE tab inside project View */
  default @Nullable @NonNls String getMinorViewId() {
    return null;
  }

  /**
   * Weight is used to provide an order in SelectIn popup. Lesser weights come first.
   * @return weight of this particular target.
   */
  default float getWeight() {
    return 0;
  }
}
