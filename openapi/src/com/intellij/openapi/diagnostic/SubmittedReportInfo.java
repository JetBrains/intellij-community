package com.intellij.openapi.diagnostic;

/**
 * Simple bean representing error submission status.
 */
public class SubmittedReportInfo {
  public static class SubmissionStatus {
    /**
     * Issue have been succesfully created
     */
    public static SubmissionStatus NEW_ISSUE = new SubmissionStatus("NEW_ISSUE");

    /**
     * Issue user have been trying to submit is actually a duplicate of existing one
     */
    public static SubmissionStatus DUPLICATE = new SubmissionStatus("DUPLICATE");

    /**
     * Submission failed. (For network connection reasons for example)
     */
    public static SubmissionStatus FAILED = new SubmissionStatus("FAILED");
    private String myName;

    private SubmissionStatus(String name) {
      myName = name;
    }

    public String toString() {
      return "SubmissionStatus[" + myName + "]";
    }
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
