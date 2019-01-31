// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.jshell.protocol;

import java.io.Serializable;

/**
 * @author Eugene Zhuravlev
 */
public class Event implements Serializable {
  private CodeSnippet myCauseSnippet;
  private CodeSnippet mySnippet;
  private CodeSnippet.Status myPreviousStatus;
  private CodeSnippet.Status myStatus;
  private String myValue;
  private String myExceptionText;
  private String myDiagnostic;

  @SuppressWarnings("unused")
  public Event() { }

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

  public CodeSnippet.Status getStatus() {
    return myStatus;
  }

  public String getValue() {
    return myValue;
  }

  public CodeSnippet getCauseSnippet() {
    return myCauseSnippet;
  }

  public CodeSnippet getSnippet() {
    return mySnippet;
  }

  public String getExceptionText() {
    return myExceptionText;
  }

  public void setExceptionText(String exceptionText) {
    myExceptionText = exceptionText;
  }

  public String getDiagnostic() {
    return myDiagnostic;
  }

  public void setDiagnostic(String diagnostic) {
    myDiagnostic = diagnostic;
  }
}