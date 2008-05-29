package com.intellij.lang;

/**
 * @author yole
 */
public interface ExtensionFactory {
  Object createInstance(final String factoryArgument, String implementationClass);
}
