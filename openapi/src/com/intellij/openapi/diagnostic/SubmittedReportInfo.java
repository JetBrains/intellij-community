package com.intellij.openapi.diagnostic;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 17, 2005
 * Time: 9:42:38 PM
 * To change this template use File | Settings | File Templates.
 */
public class SubmittedReportInfo {
  public static class SubmissionStatus {
    public static SubmissionStatus NEW_ISSUE = new SubmissionStatus("NEW_ISSUE");
    public static SubmissionStatus DUPLICATE = new SubmissionStatus("DUPLICATE");
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
