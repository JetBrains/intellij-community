// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

/**
 * @param <T> item type
 */
public interface TypeVisibilityStateHolder<T> {

  /**
   * Set filtering state for item type
   *
   * @param type  a type to update
   * @param value if false, a file type will be filtered out
   */
  void setVisible(T type, boolean value);

  /**
   * Check if type should be filtered out
   *
   * @param type a type to check
   * @return false if items of the specified type should be filtered out
   */
  boolean isVisible(T type);
}
