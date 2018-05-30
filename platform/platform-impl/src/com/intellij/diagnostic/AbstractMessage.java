// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public abstract class AbstractMessage {
  private final Date myDate = Calendar.getInstance().getTime();
  private boolean myIsRead;
  private Runnable myOnReadCallback;
  private boolean myIsSubmitting;
  private SubmittedReportInfo mySubmissionInfo;
  private String myAdditionalInfo;
  private Integer myAssigneeId;

  public abstract @NotNull Throwable getThrowable();
  public abstract @NotNull String getThrowableText();

  /** Returns a user message (see {@link LogMessage#createEvent}), if present. */
  public abstract @Nullable String getMessage();

  /** Returns a (possibly empty) list of all attachments. */
  public @NotNull List<Attachment> getAllAttachments() {
    return Collections.emptyList();
  }

  /** Returns a list of attachments marked by a user to be included into the error report. */
  public @NotNull List<Attachment> getIncludedAttachments() {
    return ContainerUtil.filter(getAllAttachments(), Attachment::isIncluded);
  }

  public @NotNull Date getDate() {
    return myDate;
  }

  public boolean isRead() {
    return myIsRead;
  }

  public void setRead(boolean isRead) {
    myIsRead = isRead;
    if (isRead && myOnReadCallback != null) {
      myOnReadCallback.run();
      myOnReadCallback = null;
    }
  }

  public void setOnReadCallback(Runnable callback) {
    myOnReadCallback = callback;
  }

  public boolean isSubmitting() {
    return myIsSubmitting;
  }

  public void setSubmitting(boolean isSubmitting) {
    myIsSubmitting = isSubmitting;
  }

  public SubmittedReportInfo getSubmissionInfo() {
    return mySubmissionInfo;
  }

  public void setSubmitted(SubmittedReportInfo info) {
    myIsSubmitting = false;
    mySubmissionInfo = info;
  }

  public boolean isSubmitted() {
    return mySubmissionInfo != null &&
           (mySubmissionInfo.getStatus() == SubmittedReportInfo.SubmissionStatus.NEW_ISSUE ||
            mySubmissionInfo.getStatus() == SubmittedReportInfo.SubmissionStatus.DUPLICATE);
  }

  public String getAdditionalInfo() {
    return myAdditionalInfo;
  }

  public void setAdditionalInfo(String additionalInfo) {
    myAdditionalInfo = additionalInfo;
  }

  public @Nullable Integer getAssigneeId() {
    return myAssigneeId;
  }

  public void setAssigneeId(@Nullable Integer assigneeId) {
    myAssigneeId = assigneeId;
  }

  /** @deprecated use {@link #getIncludedAttachments()} instead (to be removed in IDEA 2020) */
  public List<Attachment> getAttachments() {
    return getIncludedAttachments();
  }
}