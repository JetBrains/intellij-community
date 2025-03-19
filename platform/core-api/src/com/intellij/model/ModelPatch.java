// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @deprecated failed experiment; will be removed
 */
@Deprecated
@ApiStatus.ScheduledForRemoval
public interface ModelPatch {
  /**
   * @return a map from original files to their new contents in the branch
   */
  @NotNull Map<VirtualFile, CharSequence> getBranchChanges();

  /**
   * Apply the branch changes to the real model. Should be called in a write action.
   */
  void applyBranchChanges();
}
