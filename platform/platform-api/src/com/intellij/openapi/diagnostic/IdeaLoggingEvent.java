// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public class IdeaLoggingEvent {
  private final String myMessage;
  private final Throwable myThrowable;
  private final List<Attachment> myAttachments;
  private final @Nullable IdeaPluginDescriptor myPlugin;
  private final @Nullable Object myData;
  private final @Nullable ProblematicPluginInfo myProblematicPluginInfo;
  private final @NotNull UnhandledExceptionKind myUnhandledExceptionKind;

  public IdeaLoggingEvent(String message, Throwable throwable) {
    this(message, throwable, null);
  }

  public IdeaLoggingEvent(String message, Throwable throwable, @Nullable Object data) {
    myMessage = message;
    myAttachments = List.of();
    myPlugin = null;
    myProblematicPluginInfo = null;
    myData = data;
    // The throwable is raw here, so it still carries the kind. Keep the cause and the kind apart. See IJPL-254578.
    var unwrapped = throwable == null ? null : UnhandledException.unwrapIfUnhandled(throwable);
    myThrowable = unwrapped == null ? null : unwrapped.getRealCause();
    myUnhandledExceptionKind = unwrapped == null ? UnhandledExceptionKind.HANDLED : unwrapped.getUnhandledExceptionKind();
  }

  /**
   * A caller passes {@code throwable} without an {@link UnhandledException} wrapper, so it must pass
   * {@code unhandledExceptionKind} too. See IJPL-254578.
   */
  @ApiStatus.Internal
  public IdeaLoggingEvent(
    @Nullable String message,
    @NotNull Throwable throwable,
    @NotNull List<Attachment> attachments,
    @Nullable ProblematicPluginInfo problematicPluginInfo,
    @Nullable Object data,
    @NotNull UnhandledExceptionKind unhandledExceptionKind
  ) {
    myMessage = message;
    myThrowable = throwable;
    myAttachments = attachments;
    myData = data;
    myProblematicPluginInfo = problematicPluginInfo;
    myUnhandledExceptionKind = unhandledExceptionKind;
    myPlugin = problematicPluginInfo instanceof ProblematicPluginInfoWithDescriptor
               ? ((ProblematicPluginInfoWithDescriptor)problematicPluginInfo).getPluginDescriptor()
               : null;
  }

  /** Returns a message passed to {@link Logger#error Logger.error(String, [...])} methods. */
  public final @Nullable String getMessage() {
    return myMessage;
  }

  /**
   * Returns the real cause, without an {@link UnhandledException} wrapper.
   * An {@code UnhandledException} carries no useful information, so a report must show the real cause. See IJPL-254578.
   * <p>
   * If the object comes from {@link com.intellij.diagnostic.IdeErrorsDialog} and a text was edited by a user,
   * the returned throwable only partially resembles an original exception.
   * Prefer {@link #getThrowableText()}.
   */
  public final Throwable getThrowable() {
    return myThrowable;
  }

  /** Returns the text of {@link #getThrowable}. */
  public final @NotNull String getThrowableText() {
    return myThrowable != null ? StringUtil.getThrowableText(myThrowable) : "";
  }

  /**
   * Tells how the exception reached the error reporter. See IJPL-254578 and IJPL-100.
   */
  @ApiStatus.Internal
  public final @NotNull UnhandledExceptionKind getUnhandledExceptionKind() {
    return myUnhandledExceptionKind;
  }

  /** Returns a (possibly empty) list of attachments marked by a user to be included in the error report. */
  public final @Unmodifiable @NotNull List<Attachment> getAttachments() {
    return myAttachments;
  }

  /**
   * Returns a descriptor of a plugin in which an exception has occurred.
   * <p>
   * If the IDE is running in remote development mode and the exception was produced by the backend process, it returns {@code null}.
   * Consider using {@link #getProblematicPluginInfo()} instead which handles such cases.
   */
  public final @Nullable IdeaPluginDescriptor getPlugin() {
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
