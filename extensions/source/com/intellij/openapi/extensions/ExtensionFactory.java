package com.intellij.openapi.extensions;

/**
 * @author yole
 */
public interface ExtensionFactory {
  Object createInstance(final String factoryArgument, String implementationClass);
}
