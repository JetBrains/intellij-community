// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class DefaultLineFlags {
  public static final DefaultLineFlags DEFAULT = new DefaultLineFlags(false);
  public static final DefaultLineFlags IGNORED = new DefaultLineFlags(true);

  public boolean isIgnored;

  private DefaultLineFlags(boolean isIgnored) {
    this.isIgnored = isIgnored;
  }
}
