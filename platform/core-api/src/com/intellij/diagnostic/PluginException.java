// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.ExceptionWithAttachments;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Represents an internal error caused by a plugin. It may happen if the plugin's code fails with an exception, or if the plugin violates
 * some contract of IntelliJ Platform. If such exceptions are thrown or logged via {@link Logger#error(Throwable)}
 * method and reported to JetBrains by user, they may be automatically attributed to corresponding plugins.
 *
 * <p> If the problem is caused by a class, use {@link #createByClass} to create
 * an instance. If the problem is caused by an extension, implement {@link com.intellij.openapi.extensions.PluginAware} in its extension class
 * to get the plugin ID.
 */
public class PluginException extends RuntimeException implements ExceptionWithAttachments {
  private final PluginId myPluginId;
  private final List<Attachment> attachments;

  public PluginException(@NotNull @NonNls String message, Throwable cause, @Nullable PluginId pluginId) {
    super(message, cause);
    myPluginId = pluginId;
    attachments = Collections.emptyList();
  }

  public PluginException(@NotNull Throwable e, @Nullable PluginId pluginId) {
    super (e.getMessage(), e);
    myPluginId = pluginId;
    attachments = Collections.emptyList();
  }

  public PluginException(@NotNull @NonNls String message, @Nullable PluginId pluginId) {
    super(message);
    myPluginId = pluginId;
    attachments = Collections.emptyList();
  }

  public PluginException(@NotNull @NonNls String message, @Nullable PluginId pluginId, @NotNull List<Attachment> attachments) {
    super(message);
    myPluginId = pluginId;
    this.attachments = attachments;
  }

  public final @Nullable PluginId getPluginId() {
    return myPluginId;
  }

  @Override
  @NotNull
  public @NonNls String getMessage() {
    String message = super.getMessage();
    // do not add suffix with plugin id if plugin info is already in message
    if (myPluginId == null || (message != null && message.contains("PluginDescriptor("))) {
      return message;
    }
    else {
      return Strings.notNullize(message) + " [Plugin: " + myPluginId + "]";
    }
  }

  @Override
  public final Attachment @NotNull [] getAttachments() {
    return attachments.toArray(Attachment.EMPTY_ARRAY);
  }

  /**
   * Creates an exception caused by a problem in a plugin's code.
   * @param pluginClass a problematic class which caused the error
   */
  @NotNull
  public static PluginException createByClass(@NotNull @NonNls String errorMessage, @Nullable Throwable cause, @NotNull Class<?> pluginClass) {
    return PluginProblemReporter.getInstance().createPluginExceptionByClass(errorMessage, cause, pluginClass);
  }

  /**
   * Creates an exception caused by a problem in a plugin's code, takes error message from the cause exception.
   * @param pluginClass a problematic class which caused the error
   */
  @NotNull
  public static PluginException createByClass(@NotNull Throwable cause, @NotNull Class<?> pluginClass) {
    return PluginProblemReporter.getInstance().createPluginExceptionByClass(Strings.notNullize(cause.getMessage()), cause, pluginClass);
  }

  /**
   * Log an error caused by a problem in a plugin's code.
   * @param pluginClass a problematic class which caused the error
   */
  public static void logPluginError(@NotNull Logger logger, @NotNull @NonNls String errorMessage, @Nullable Throwable cause, @NotNull Class<?> pluginClass) {
    logger.error(createByClass(errorMessage, cause, pluginClass));
  }
}
