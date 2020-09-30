// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util;

import org.jetbrains.annotations.NonNls;

/**
 * @deprecated see {@link LogicalRootsManager} for details
 */
@Deprecated
public final class LogicalRootType<T extends LogicalRoot> {
  public static final LogicalRootType<VirtualFileLogicalRoot> SOURCE_ROOT = create("Source root");
  private final String myName;


  private LogicalRootType(final String name) {
    myName = name;
  }

  @NonNls
  public String toString() {
    return "Logical root type:" + myName;
  }

  public static <T extends LogicalRoot> LogicalRootType<T> create(@NonNls String name) {
    return new LogicalRootType<>(name);
  }
}
