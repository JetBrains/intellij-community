// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.ExceptionWithAttachments;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * <p>Represents an internal error caused by a plugin. It may happen if the plugin's code fails with an exception, or if the plugin violates
 * some contract of IntelliJ Platform. If such exceptions are thrown or logged via {@link Logger#error(Throwable)}
 * method and reported to JetBrains by user, they may be automatically attributed to corresponding plugins.</p>
 *
 * <p>If the problem is caused by a class, use {@link #createByClass} to create
 * an instance. If the problem is caused by an extension, implement {@link com.intellij.openapi.extensions.PluginAware} in its extension class
 * to get the plugin ID.</p>
 */
public class PluginException extends RuntimeException implements ExceptionWithAttachments {
  private final PluginId myPluginId;
  private final List<Attachment> myAttachments;

  public PluginException(@NotNull String message, Throwable cause, @Nullable PluginId pluginId) {
    super(message, cause);
    myPluginId = pluginId;
    myAttachments = Collections.emptyList();
  }

  public PluginException(@NotNull Throwable e, @Nullable PluginId pluginId) {
    super(e.getMessage(), e);
    myPluginId = pluginId;
    myAttachments = Collections.emptyList();
  }

  public PluginException(@NotNull String message, @Nullable PluginId pluginId) {
    super(message);
    myPluginId = pluginId;
    myAttachments = Collections.emptyList();
  }

  public PluginException(@NotNull String message, @Nullable PluginId pluginId, @NotNull List<Attachment> attachments) {
    super(message);
    myPluginId = pluginId;
    myAttachments = attachments;
  }

  public PluginException(@NotNull String message,
                         @Nullable Throwable cause,
                         @Nullable PluginId pluginId,
                         @NotNull List<Attachment> attachments) {
    super(message, cause);
    myPluginId = pluginId;
    myAttachments = attachments;
  }

  public final @Nullable PluginId getPluginId() {
    return myPluginId;
  }

  @Override
  public @NotNull String getMessage() {
    String message = super.getMessage();
    // do not add suffix with plugin id if plugin info is already in message
    if (myPluginId == null || (message != null && message.contains("PluginDescriptor("))) {
      return message;
    }
    else {
      return (message != null ? message : "null") + " [Plugin: " + myPluginId + "]";
    }
  }

  @Override
  public final Attachment @NotNull [] getAttachments() {
    return myAttachments.toArray(Attachment.EMPTY_ARRAY);
  }

  /**
   * Creates an exception caused by a problem in a plugin's code.
   *
   * @param pluginClass a problematic class which caused the error
   */
  public static @NotNull PluginException createByClass(@NotNull String errorMessage,
                                                       @Nullable Throwable cause,
                                                       @NotNull Class<?> pluginClass) {
    return PluginProblemReporter.getInstance().createPluginExceptionByClass(errorMessage, cause, pluginClass);
  }

  /**
   * Creates an exception caused by a problem in a plugin's code, takes error message from the cause exception.
   *
   * @param pluginClass a problematic class which caused the error
   */
  public static @NotNull PluginException createByClass(@NotNull Throwable cause, @NotNull Class<?> pluginClass) {
    String message = cause.getMessage();
    return PluginProblemReporter.getInstance().createPluginExceptionByClass(message != null ? message : "", cause, pluginClass);
  }

  /**
   * Log an error caused by a problem in a plugin's code.
   *
   * @param pluginClass a problematic class which caused the error
   */
  public static void logPluginError(@NotNull Logger logger,
                                    @NotNull String errorMessage,
                                    @Nullable Throwable cause,
                                    @NotNull Class<?> pluginClass) {
    logger.error(createByClass(errorMessage, cause, pluginClass));
  }

  public static void reportDeprecatedUsage(@NotNull String signature, @NotNull String details) {
    String message = "`" + signature + "` is deprecated and going to be removed soon. " + details;
    PluginException t = new PluginException(message, null);
    // trim stacktrace to avoid multiple reports in logs with the same deprecated signature
    t.setStackTrace(ArrayUtil.realloc(t.getStackTrace(), 3, StackTraceElement[]::new));
    Logger.getInstance(PluginException.class).error(t);
  }

  public static void reportDeprecatedUsage(@NotNull Class<?> violator, @NotNull String signature, @NotNull String details) {
    String message = "`" + signature + "` is deprecated and going to be removed soon. " + details;
    PluginException t = createByClass(message, null, violator);
    // trim stacktrace to avoid multiple reports in logs with the same deprecated signature
    t.setStackTrace(ArrayUtil.realloc(t.getStackTrace(), 5, StackTraceElement[]::new));
    Logger.getInstance(violator).error(t);
  }

  public static void reportDeprecatedDefault(@NotNull Class<?> violator, @NotNull String methodName, @NotNull String details) {
    String message = "The default implementation of method `" + methodName + "` is deprecated, " +
                     "`" + violator.getName() + "` must override it. " + details;
    PluginException t = createByClass(message, null, violator);
    // trim stacktrace to avoid multiple reports in logs with the same deprecated method
    t.setStackTrace(ArrayUtil.realloc(t.getStackTrace(), 5, StackTraceElement[]::new));
    Logger.getInstance(violator).error(t);
  }
}
