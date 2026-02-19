// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.List;

public class IdeaLoggingEvent {
  private final String myMessage;
  private final Throwable myThrowable;
  private final List<Attachment> myAttachments;
  private final @Nullable IdeaPluginDescriptor myPlugin;
  private final @Nullable Object myData;
  private final @Nullable ProblematicPluginInfo myProblematicPluginInfo;

  public IdeaLoggingEvent(String message, Throwable throwable) {
    this(message, throwable, null);
  }

  public IdeaLoggingEvent(String message, Throwable throwable, @Nullable Object data) {
    myMessage = message;
    myThrowable = throwable;
    myAttachments = List.of();
    myPlugin = null;
    myProblematicPluginInfo = null;
    myData = data;
  }

  @ApiStatus.Internal
  public IdeaLoggingEvent(
    @Nullable String message,
    @NotNull Throwable throwable,
    @NotNull List<Attachment> attachments,
    @Nullable ProblematicPluginInfo problematicPluginInfo,
    @Nullable Object data
  ) {
    myMessage = message;
    myThrowable = throwable;
    myAttachments = attachments;
    myData = data;
    myProblematicPluginInfo = problematicPluginInfo;
    myPlugin = problematicPluginInfo instanceof ProblematicPluginInfoBasedOnDescriptor ? ((ProblematicPluginInfoBasedOnDescriptor)problematicPluginInfo).getPluginDescriptor() : null;
  }

  public IdeaLoggingEvent(
    @Nullable String message,
    @NotNull Throwable throwable,
    @NotNull List<Attachment> attachments,
    @Nullable IdeaPluginDescriptor plugin,
    @Nullable Object data
  ) {
    myMessage = message;
    myThrowable = throwable;
    myAttachments = Collections.unmodifiableList(attachments);
    myPlugin = plugin;
    myProblematicPluginInfo = plugin != null ? new ProblematicPluginInfoBasedOnDescriptor(plugin) : null;
    myData = data;
  }

  /** Returns a message passed to {@link Logger#error Logger.error(String, [...])} methods. */
  public @Nullable String getMessage() {
    return myMessage;
  }

  /**
   * Returns a throwable.
   * If the object comes from {@link com.intellij.diagnostic.IdeErrorsDialog} and a text was edited by a user,
   * the returned throwable only partially resembles an original exception.
   * Prefer {@link #getThrowableText()}.
   */
  public Throwable getThrowable() {
    return myThrowable;
  }

  public @NotNull String getThrowableText() {
    return myThrowable != null ? StringUtil.getThrowableText(myThrowable) : "";
  }

  /** Returns a (possibly empty) list of attachments marked by a user to be included in the error report. */
  public @Unmodifiable @NotNull List<Attachment> getAttachments() {
    return myAttachments;
  }

  /**
   * Returns a descriptor of a plugin in which an exception has occurred.
   * <p>
   * If the IDE is running in remote development mode and the exception was produced by the backend process, it returns {@code null}.
   * Consider using {@link #getProblematicPluginInfo()} instead which handles such cases.
   */
  public @Nullable IdeaPluginDescriptor getPlugin() {
    return myPlugin;
  }

  /**
   * Returns information about a plugin that caused the exception.
   */
  @ApiStatus.Experimental
  public final @Nullable ProblematicPluginInfo getProblematicPluginInfo() {
    return myProblematicPluginInfo;
  }

  public @Nullable Object getData() {
    return myData;
  }

  @Override
  public String toString() {
    return "IdeaLoggingEvent[message=" + myMessage + ", throwable=" + getThrowableText() + "]";
  }
}
