// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import org.jetbrains.annotations.NotNull;

/**
 * This exception is thrown if there is a critical problem with some loaded extension. Use this exception inside 'intellij.platform.extensions'
 * module only, in other parts of IntelliJ Platform and its plugins use {@link com.intellij.diagnostic.PluginException#createByClass} instead.
 *
 * @see ExtensionInstantiationException
 */
public class ExtensionException extends RuntimeException{
  private final Class<?> myExtensionClass;

  public ExtensionException(@NotNull String message, @NotNull Class<?> extensionClass) {
    super(message);
    myExtensionClass = extensionClass;
  }

  public ExtensionException(@NotNull Class<?> extensionClass) {
    super(extensionClass.getCanonicalName());

    myExtensionClass = extensionClass;
  }

  public ExtensionException(@NotNull Class<?> extensionClass, @NotNull Throwable cause) {
    super(extensionClass.getCanonicalName(), cause);

    myExtensionClass = extensionClass;
  }

  public @NotNull Class<?> getExtensionClass() {
    return myExtensionClass;
  }
}
