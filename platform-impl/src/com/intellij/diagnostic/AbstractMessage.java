/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.SubmittedReportInfo;

import java.util.Calendar;
import java.util.Date;

public abstract class AbstractMessage {

  private boolean myIsRead = false;
  private SubmittedReportInfo mySubmissionInfo;
  private String myScrID;

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
  }

  public void setSubmitted(SubmittedReportInfo info) {
    mySubmissionInfo = info;
  }

  public SubmittedReportInfo getSubmissionInfo() {
    return mySubmissionInfo;
  }

  public boolean isSubmitted() {
    return mySubmissionInfo != null &&
          (mySubmissionInfo.getStatus() == SubmittedReportInfo.SubmissionStatus.NEW_ISSUE ||
           mySubmissionInfo.getStatus() == SubmittedReportInfo.SubmissionStatus.DUPLICATE);
  }

  public Date getDate() {
    return myDate;
  }

}
