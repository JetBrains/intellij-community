// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.caches;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nullable;

public abstract class CachesInvalidator {
  public static final ExtensionPointName<CachesInvalidator> EP_NAME = new ExtensionPointName<>("com.intellij.cachesInvalidator");

  /**
   * @return description of the files to be cleared, shown in the warning dialog to the user.
   *         When to use: when invalidation will lead to the loss of a potentially valuable to the user information, e.g. Local History.
   *         Do not use:  when caches are easily re-buildable and doesn't contain user's data (to avoid unnecessary confusion).
   */
  @Nullable
  public String getDescription() { return null; }

  /**
   * The method should not consume significant time.
   * All the clearing operations should be executed after IDE relaunches.
   */
  public abstract void invalidateCaches();
}
