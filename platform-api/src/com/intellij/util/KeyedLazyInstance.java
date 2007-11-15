/*
 * @author max
 */
package com.intellij.util;

public interface KeyedLazyInstance<T> {
  String getKey();
  T getInstance();
}