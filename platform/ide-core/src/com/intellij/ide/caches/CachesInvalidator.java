// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.caches;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

public abstract class CachesInvalidator {
  public static final ExtensionPointName<CachesInvalidator> EP_NAME = new ExtensionPointName<>("com.intellij.cachesInvalidator");

  /**
   * @return description of the files to be cleared, shown in the warning dialog to the user.
   *         When to use: when invalidation will lead to the loss of a potentially valuable to the user information, e.g. Local History.
   *         Do not use:  when caches are easily re-buildable and doesn't contain user's data (to avoid unnecessary confusion).
   */
  @Nullable
  @NlsContexts.Checkbox
  public String getDescription() { return null; }


  /**
   * Additional comment and description for the given caches invaludate action
   */
  @Nullable
  @NlsContexts.DetailedDescription
  public String getComment() { return null; }

  /**
   * The method should not consume significant time.
   * All the clearing operations should be executed after IDE relaunches.
   */
  public abstract void invalidateCaches();

  /**
   * Shows a checkbox for this {@link CachesInvalidator}
   * with its {@link #getDescription()} as checkbox label
   * and uses the returned non-null value as checkbox default
   * state.
   * The {@link #invalidateCaches()} method is only executed
   * if the check-box is checked
   */
  @Nullable
  public Boolean optionalCheckboxDefaultValue() { return null; }
}
