// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.IdeaLogRecordFormatter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.UnhandledException;
import com.intellij.openapi.diagnostic.UnhandledExceptionKind;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Internal API. See a note in {@link MessagePool}. */
@ApiStatus.Internal
public final class LogMessage extends AbstractMessage {
  private final Throwable myThrowable;
  private final @NotNull UnhandledExceptionKind myUnhandledExceptionKind;
  private final String myMessage;
  private final List<Attachment> myAttachments;

  /**
   * Takes {@code throwable} with an {@link UnhandledException} wrapper, and splits it.
   * Prefer the overload with an explicit kind. A caller that already holds the real cause must pass it. See IJPL-254578.
   */
  public LogMessage(@NotNull Throwable throwable, @Nullable String message, @NotNull List<Attachment> attachments) {
    this(UnhandledException.unwrapIfUnhandled(throwable).getRealCause(),
         message,
         attachments,
         UnhandledException.unwrapIfUnhandled(throwable).getUnhandledExceptionKind());
  }

  /**
   * @param realCause              the cause without an {@link UnhandledException} wrapper
   * @param unhandledExceptionKind see {@link AbstractMessage#getUnhandledExceptionKind}
   */
  public LogMessage(@NotNull Throwable realCause,
                    @Nullable String message,
                    @NotNull List<Attachment> attachments,
                    @NotNull UnhandledExceptionKind unhandledExceptionKind) {
    myThrowable = ThrowableInterner.intern(realCause);
    myUnhandledExceptionKind = unhandledExceptionKind;

    var str = message;
    if (str != null && realCause.getMessage() != null) {
      str = Strings.trimStart(str, realCause.getMessage());
      if (!Strings.areSameInstance(str, message)) {
        str = Strings.trimStart(str, ": ");
      }
    }
    if ("null".equals(str)) {
      str = null;
    }
    myMessage = Strings.nullize(str, true);

    myAttachments = new ArrayList<>(ContainerUtil.filter(attachments, attachment -> attachment != null));
  }

  @Override
  public @NotNull Throwable getThrowable() {
    return myThrowable;
  }

  @Override
  public @NotNull String getThrowableText() {
    return IdeaLogRecordFormatter.formatThrowable(myThrowable);
  }

  @Override
  public @NotNull UnhandledExceptionKind getUnhandledExceptionKind() {
    return myUnhandledExceptionKind;
  }

  @Override
  public @NotNull String getMessage() {
    return myMessage != null ? myMessage : "";
  }

  @Override
  public @NotNull List<Attachment> getAllAttachments() {
    return Collections.unmodifiableList(myAttachments);
  }

  /** @deprecated use {@link IdeaLoggingEvent#IdeaLoggingEvent} directly */
  @Deprecated(forRemoval = true)
  public static IdeaLoggingEvent createEvent(@NotNull Throwable throwable, @Nullable String userMessage, Attachment @NotNull ... attachments) {
    return new IdeaLoggingEvent(userMessage, throwable, new LogMessage(throwable, userMessage, List.of(attachments)));
  }
}
