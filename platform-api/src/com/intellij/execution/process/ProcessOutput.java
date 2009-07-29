package com.intellij.execution.process;

import com.intellij.openapi.util.text.StringUtil;

import java.util.List;

/**
 * @author yole
 */
public class ProcessOutput {
  private StringBuilder myStdoutBuilder = new StringBuilder();
  private StringBuilder myStderrBuilder = new StringBuilder();
  private int myExitCode;
  private boolean myTimeout;

  public ProcessOutput() {
  }

  void appendStdout(String text) {
    myStdoutBuilder.append(text);
  }

  void appendStderr(String text) {
    myStderrBuilder.append(text);
  }

  void setExitCode(int exitCode) {
    myExitCode = exitCode;
  }

  public String getStdout() {
    return myStdoutBuilder.toString();
  }

  public String getStderr() {
    return myStderrBuilder.toString();
  }

  public int getExitCode() {
    return myExitCode;
  }

  void setTimeout() {
    myTimeout = true;
  }

  public boolean isTimeout() {
    return myTimeout;
  }

  public List<String> getStdoutLines() {
    return splitLines(myStdoutBuilder.toString());
  }

  public List<String> getStderrLines() {
    return splitLines(myStderrBuilder.toString());
  }

  private List<String> splitLines(String s) {
    String converted = StringUtil.convertLineSeparators(s);
    return StringUtil.split(converted, "\n");
  }
}
