package com.intellij.openapi.extensions;

/**
 * @author mike
 */
public interface ExtensionInitializer {
  void initExtension(Object extension);
}
