/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diagnostic;

import com.intellij.notification.Notification;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public abstract class AbstractMessage {

  private boolean myIsRead = false;
  private boolean myIsSubmitting = false;
  private SubmittedReportInfo mySubmissionInfo;
  private String myAdditionalInfo;
  private Notification myNotification;
  private Integer myAssigneeId;

  private final Date myDate;

  public AbstractMessage() {
    myDate = Calendar.getInstance().getTime();
  }

  public abstract String getThrowableText();
  public abstract Throwable getThrowable();
  public abstract String getMessage();

  public boolean isRead() {
    return myIsRead;
  }

  public void setRead(boolean aReadFlag) {
    myIsRead = aReadFlag;
    if (myNotification != null && aReadFlag) {
      myNotification.expire();
      myNotification = null;
    }
  }

  public void setSubmitted(SubmittedReportInfo info) {
    myIsSubmitting = false;
    mySubmissionInfo = info;
  }

  public SubmittedReportInfo getSubmissionInfo() {
    return mySubmissionInfo;
  }

  public void setNotification(Notification notification) {
    myNotification = notification;
  }

  public boolean isSubmitting() {
    return myIsSubmitting;
  }

  public void setSubmitting(boolean isSubmitting) {
    myIsSubmitting = isSubmitting;
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

  public Date getDate() {
    return myDate;
  }

  public Integer getAssigneeId() {
    return myAssigneeId;
  }

  public void setAssigneeId(Integer assigneeId) {
    myAssigneeId = assigneeId;
  }

  public List<Attachment> getAllAttachments() {
    return Collections.emptyList();
  }

  /**
   * @return list of attachments which are marked by user to be included into the error report
   */
  public List<Attachment> getIncludedAttachments() {
    return ContainerUtil.filter(getAllAttachments(), Attachment::isIncluded);
  }

  /**
   * @deprecated use {@link #getIncludedAttachments()} instead
   */
  @NotNull 
  public List<Attachment> getAttachments() {
    return getIncludedAttachments();
  }
}
