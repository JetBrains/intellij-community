/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.diagnostic;

/**
 * Simple bean representing error submission status.
 */
public class SubmittedReportInfo {
  public enum SubmissionStatus {
    /**
     * Issue have been succesfully created
     */
    NEW_ISSUE,

    /**
     * Issue is actually a duplicate of existing one
     */
    DUPLICATE,

    /**
     * Submission failed. (For network connection reasons for example)
     */
    FAILED
  }

  private String myURL;
  private String myLinkText;
  private SubmissionStatus myStatus;

  /**
   * Create new submission status bean
   * @param URL url that points to newly created issue. Optional. Pass <code>null</code> value if N/A or failed
   * @param linkText short text that UI interface pointing to the issue should have.
   * @param status submission success/failure
   */
  public SubmittedReportInfo(final String URL, final String linkText, final SubmissionStatus status) {
    myURL = URL;
    myLinkText = linkText;
    myStatus = status;
  }

  public String getURL() {
    return myURL;
  }

  public String getLinkText() {
    return myLinkText;
  }

  public SubmissionStatus getStatus() {
    return myStatus;
  }
}
