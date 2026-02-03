// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java.dependencyView;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface NamingContext {

  /**
   * @param val internal integer representation of some name in dependency graph (class name, methot name, field name, etc)
   * @return string representation corresponding to the specified numeric representation
   */
  @Nullable
  String getValue(int val);

  /**
   * @param s string representing any name in dependency graph (class name, methot name, field name, etc)
   * @return unique integer value corresponding to the specified name string
   */
  int get(String s);
}
