// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diagnostic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Simple bean representing error submission status.
 */
public class SubmittedReportInfo {
  public enum SubmissionStatus {
    /**
     * Issue have been successfully created
     */
    NEW_ISSUE,

    /**
     * Issue is actually a duplicate of existing one
     */
    DUPLICATE,

    /**
     * Submission failed (e.g. because of network problem)
     */
    FAILED
  }

  private final String myUrl;
  private final String myLinkText;
  private final SubmissionStatus myStatus;

  public SubmittedReportInfo(@NotNull SubmissionStatus status) {
    this(null, null, status);
  }

  public SubmittedReportInfo(@Nullable String url, @Nullable String linkText, @NotNull SubmissionStatus status) {
    myUrl = url;
    myLinkText = linkText;
    myStatus = status;
  }

  public String getURL() {
    return myUrl;
  }

  public String getLinkText() {
    return myLinkText;
  }

  public SubmissionStatus getStatus() {
    return myStatus;
  }
}