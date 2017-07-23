package com.intellij.execution.jshell.protocol;


import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author Eugene Zhuravlev
 * Date: 12-Jun-17
 */
@XmlRootElement
public class Event {
  
  private CodeSnippet myCauseSnippet;
  private CodeSnippet mySnippet;
  private CodeSnippet.Status myPreviousStatus;
  private CodeSnippet.Status myStatus;
  private String myValue;
  private String myExceptionText;
  private String myDiagnostic;

  public Event() {
  }

  public Event(CodeSnippet snippet, CodeSnippet causeSnippet,
               CodeSnippet.Status status, CodeSnippet.Status previousStatus,
               String value) {
    myCauseSnippet = causeSnippet;
    mySnippet = snippet;
    myPreviousStatus = previousStatus;
    myStatus = status;
    myValue = value;
  }

  public CodeSnippet.Status getPreviousStatus() {
    return myPreviousStatus;
  }

  @XmlElement
  public void setPreviousStatus(CodeSnippet.Status previousStatus) {
    myPreviousStatus = previousStatus;
  }

  public CodeSnippet.Status getStatus() {
    return myStatus;
  }

  @XmlElement
  public void setStatus(CodeSnippet.Status status) {
    myStatus = status;
  }

  public String getValue() {
    return myValue;
  }

  @XmlElement
  public void setValue(String value) {
    myValue = value;
  }

  public CodeSnippet getCauseSnippet() {
    return myCauseSnippet;
  }

  @XmlElement
  public void setCauseSnippet(CodeSnippet causeSnippet) {
    myCauseSnippet = causeSnippet;
  }

  public CodeSnippet getSnippet() {
    return mySnippet;
  }

  @XmlElement
  public void setSnippet(CodeSnippet snippet) {
    mySnippet = snippet;
  }

  public String getExceptionText() {
    return myExceptionText;
  }

  @XmlElement
  public void setExceptionText(String exceptionText) {
    myExceptionText = exceptionText;
  }

  public String getDiagnostic() {
    return myDiagnostic;
  }
  
  @XmlElement
  public void setDiagnostic(String diagnostic) {
    myDiagnostic = diagnostic;
  }
}
