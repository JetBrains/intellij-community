/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import org.jetbrains.annotations.Nullable;

/**
 * This interface may be interpreted as a reference, whose text is {@link #getStringValue()}, and resolving to
 * the result of {@link #getValue()} method.
 *
 * @author peter
 */
public interface GenericValue<T> {

  /**
   * @return the string representation of the value. Even if {@link #getValue()} returns null, this method
   * can return something more descriptive.
   */
  @TagValue
  @Nullable
  @NameValue
  String getStringValue();

  /**
   * @return resolved value. May took time. It's strongly recommended that even if T is {@link String}, one uses
   * {@link #getStringValue()} method instead. 
   */
  @Nullable
  T getValue();

}