/*
 * @author max
 */
package com.intellij.openapi.extensions;

public interface ExtensionPointAndAreaListener<T> extends ExtensionPointListener<T> {
  void areaReplaced(ExtensionsArea area);
}