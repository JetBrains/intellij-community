// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm;

/**
 * Represents a value (e.g. integers, longs, strings etc..).
 */
public interface JvmValue {
  /**
   * @return the underlying value
   */
  Object getValue();
}
