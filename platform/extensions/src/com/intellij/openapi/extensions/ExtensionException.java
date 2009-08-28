package com.intellij.openapi.extensions;

public class ExtensionException extends RuntimeException{
  private final Class myExtensionClass;

  public ExtensionException(final Class extensionClass) {
    myExtensionClass = extensionClass;
  }

  public Class getExtensionClass() {
    return myExtensionClass;
  }
}
