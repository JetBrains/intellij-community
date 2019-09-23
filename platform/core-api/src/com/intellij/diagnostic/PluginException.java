// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents an internal error caused by a plugin. It may happen if the plugin's code fails with an exception, or if the plugin violates
 * some contract of IntelliJ Platform. If such exceptions are thrown or logged via {@link Logger#error(Throwable)}
 * method and reported to JetBrains by user, they may be automatically attributed to corresponding plugins.
 *
 * <p> If the problem is caused by a class, use {@link #createByClass} to create
 * an instance. If the problem is caused by an extension, implement {@link com.intellij.openapi.extensions.PluginAware} in its extension class
 * to get the plugin ID.
 *
 * <p> In order to report problems from code in 'intellij.platform.extensions' module where this class is not accessible, use
 * {@link com.intellij.openapi.extensions.ExtensionInstantiationException} or {@link com.intellij.openapi.extensions.ExtensionException} instead.
 */
public class PluginException extends RuntimeException {
  private final PluginId myPluginId;

  public PluginException(@NotNull String message, Throwable cause, @Nullable PluginId pluginId) {
    super(message, cause);
    myPluginId = pluginId;
  }

  public PluginException(@NotNull Throwable e, @Nullable PluginId pluginId) {
    super (e.getMessage(), e);
    myPluginId = pluginId;
  }

  public PluginException(@NotNull String message, @Nullable PluginId pluginId) {
    super(message);
    myPluginId = pluginId;
  }

  @Nullable
  public PluginId getPluginId() {
    return myPluginId;
  }

  @Override
  @NotNull
  public String getMessage() {
    String message = super.getMessage();
    return myPluginId != null ? StringUtil.notNullize(message) + " [Plugin: " + myPluginId + "]" : message;
  }

  /**
   * Creates an exception caused by a problem in a plugin's code.
   * @param pluginClass a problematic class which caused the error
   */
  @NotNull
  public static PluginException createByClass(@NotNull String errorMessage, @Nullable Throwable cause, @NotNull Class<?> pluginClass) {
    return PluginProblemReporter.getInstance().createPluginExceptionByClass(errorMessage, cause, pluginClass);
  }

  /**
   * Creates an exception caused by a problem in a plugin's code, takes error message from the cause exception.
   * @param pluginClass a problematic class which caused the error
   */
  @NotNull
  public static PluginException createByClass(@NotNull Throwable cause, @NotNull Class<?> pluginClass) {
    return PluginProblemReporter.getInstance().createPluginExceptionByClass(StringUtil.notNullize(cause.getMessage()), cause, pluginClass);
  }

  /**
   * Log an error caused by a problem in a plugin's code.
   * @param pluginClass a problematic class which caused the error
   */
  public static void logPluginError(@NotNull Logger logger, @NotNull String errorMessage, @Nullable Throwable cause, @NotNull Class<?> pluginClass) {
    logger.error(createByClass(errorMessage, cause, pluginClass));
  }
}
