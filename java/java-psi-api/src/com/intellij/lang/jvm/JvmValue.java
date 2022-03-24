// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a value (e.g. integers, longs, strings etc..). In the future, this class should function as a base class for JVM method calls
 * and field references as well.
 */
public interface JvmValue {

  /**
   * Creates a long value.
   */
  static @NotNull JvmLong createLongValue(long value) {
    return new JvmLongImpl(value);
  }
}
