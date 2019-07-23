// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This exception is thrown if some extension (service, extension point) failed to initialize. Use this exception inside 'intellij.platform.extensions'
 * module only, in other parts of IntelliJ Platform and its plugins use {@link com.intellij.diagnostic.PluginException} instead.
 *
 * @see com.intellij.openapi.extensions.ExtensionException
 */
public class ExtensionInstantiationException extends RuntimeException {
  private final PluginDescriptor myExtensionOwner;

  /**
   * @param extensionOwner descriptor of the plugin which declares the problematic extension or {@code null} if it is unknown
   */
  public ExtensionInstantiationException(@NotNull String message, @Nullable PluginDescriptor extensionOwner) {
    super(message);
    myExtensionOwner = extensionOwner;
  }

  /**
   * @param extensionOwner descriptor of the plugin which declares the problematic extension or {@code null} if it is unknown
   */
  public ExtensionInstantiationException(@NotNull Throwable cause, @Nullable PluginDescriptor extensionOwner) {
    super(cause);
    myExtensionOwner = extensionOwner;
  }

  @Nullable
  public PluginDescriptor getExtensionOwner() {
    return myExtensionOwner;
  }

  @Nullable
  public PluginId getExtensionOwnerId() {
    return myExtensionOwner != null ? myExtensionOwner.getPluginId() : null;
  }

  @Override
  public String getMessage() {
    String message = super.getMessage();
    PluginId pluginId = getExtensionOwnerId();
    return pluginId != null ? StringUtil.notNullize(message) + " [Plugin " + pluginId + "]" : message;
  }
}
