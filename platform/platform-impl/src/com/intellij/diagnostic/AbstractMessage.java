// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.ProblematicPluginInfo;
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
import com.intellij.openapi.diagnostic.UnhandledException;
import com.intellij.openapi.diagnostic.UnhandledExceptionKind;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/** Internal API. See a note in {@link MessagePool}. */
@ApiStatus.Internal
public abstract class AbstractMessage {
  private final Date myDate = Calendar.getInstance().getTime();
  private boolean myIsRead;
  private Runnable myOnReadCallback;
  private boolean myIsSubmitting;
  private SubmittedReportInfo mySubmissionInfo;
  private String myAdditionalInfo;
  private String myAppInfo;

  /**
   * Returns the real cause, without an {@link UnhandledException} wrapper.
   * The wrapper carries no useful information, so every reader of an entry gets the cause. See IJPL-254578.
   */
  public abstract @NotNull Throwable getThrowable();

  public abstract @NotNull String getThrowableText();

  /**
   * Tells how the exception reached the message pool.
   * {@link #getThrowable} drops the {@link UnhandledException} wrapper, so the entry carries this kind on its own.
   * See IJPL-254578.
   */
  public @NotNull UnhandledExceptionKind getUnhandledExceptionKind() {
    return UnhandledExceptionKind.HANDLED;
  }

  /** Who sends a report. It decides which attachments the report gets. */
  public enum ReportKind {
    /** A user sends the report from the error dialog, and gets the attachments that a user opted in. */
    USER,
    /** The IDE sends the report without a user. An attachment is opt-in, and no user could opt in, so all of them go. */
    AUTOMATIC
  }

  /**
   * Builds the event that an error reporter gets.
   * The entry gives the kind, so a caller cannot forget it. See IJPL-254578.
   * The entry is also the event data, because a reporter reads the date and the app info from it.
   */
  public final @NotNull IdeaLoggingEvent toLoggingEvent(@NotNull ReportKind kind,
                                                        @Nullable ProblematicPluginInfo problematicPluginInfo) {
    return toLoggingEvent(kind, problematicPluginInfo, getMessage(), getThrowable());
  }

  /**
   * Builds the event for a text that a user edited in the error dialog.
   * The event keeps every other field of the entry, so it also keeps the kind. See IJPL-254578.
   */
  public final @NotNull IdeaLoggingEvent toLoggingEvent(@NotNull ReportKind kind,
                                                        @Nullable ProblematicPluginInfo problematicPluginInfo,
                                                        @Nullable String editedMessage,
                                                        @NotNull Throwable editedThrowable) {
    List<Attachment> attachments = kind == ReportKind.USER ? getIncludedAttachments() : getAllAttachments();
    return new IdeaLoggingEvent(editedMessage, editedThrowable, attachments, problematicPluginInfo, this,
                                getUnhandledExceptionKind());
  }

  /** Returns a message passed along with a throwable to {@link com.intellij.openapi.diagnostic.Logger#error}, if present. */
  public abstract @Nullable String getMessage();

  /** Returns a (possibly empty) list of all attachments. */
  public @NotNull @Unmodifiable List<Attachment> getAllAttachments() {
    return List.of();
  }

  public final @NotNull @Unmodifiable List<Attachment> getIncludedAttachments() {
    return ContainerUtil.filter(getAllAttachments(), Attachment::isIncluded);
  }

  public final @NotNull Date getDate() {
    return myDate;
  }

  public final boolean isRead() {
    return myIsRead;
  }

  public final void setRead(boolean isRead) {
    myIsRead = isRead;
    if (isRead && myOnReadCallback != null) {
      myOnReadCallback.run();
      myOnReadCallback = null;
    }
  }

  public final void setOnReadCallback(Runnable callback) {
    myOnReadCallback = callback;
  }

  public final boolean isSubmitting() {
    return myIsSubmitting;
  }

  public final void setSubmitting(boolean isSubmitting) {
    myIsSubmitting = isSubmitting;
  }

  public final SubmittedReportInfo getSubmissionInfo() {
    return mySubmissionInfo;
  }

  public final boolean isSubmitted() {
    return mySubmissionInfo != null &&
           (mySubmissionInfo.getStatus() == SubmittedReportInfo.SubmissionStatus.NEW_ISSUE ||
            mySubmissionInfo.getStatus() == SubmittedReportInfo.SubmissionStatus.DUPLICATE);
  }

  public final void setSubmitted(SubmittedReportInfo info) {
    myIsSubmitting = false;
    mySubmissionInfo = info;
  }

  public final String getAdditionalInfo() {
    return myAdditionalInfo;
  }

  public final void setAdditionalInfo(String additionalInfo) {
    myAdditionalInfo = additionalInfo;
  }

  protected final @Nullable String getAppInfo() {
    return myAppInfo;
  }

  protected final void setAppInfo(String appInfo) {
    myAppInfo = appInfo;
  }
}
