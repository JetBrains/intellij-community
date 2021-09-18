// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm;

/**
 * Represents a long primitive value.
 */
public interface JvmLong extends JvmValue {
  /**
   * @return The underlying long value.
   */
  long getLongValue();
}