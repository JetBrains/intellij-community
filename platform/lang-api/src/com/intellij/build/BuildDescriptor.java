// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build;

import com.intellij.build.events.BuildEventsNls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public interface BuildDescriptor {
  @NotNull
  Object getId();

  /**
   * The existence of a group id signalizes that
   * this descriptor is associated with some other
   * builds which have the same groupId.
   */
  default @Nullable Object getGroupId() {
    return null;
  }

  @NotNull
  @BuildEventsNls.Title
  String getTitle();

  @NotNull
  @NonNls
  String getWorkingDir();

  long getStartTime();
}
