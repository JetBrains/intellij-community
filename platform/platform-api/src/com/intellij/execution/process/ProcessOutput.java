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
package com.intellij.execution.process;

import com.intellij.openapi.util.text.StringUtil;

import java.util.List;

/**
 * @author yole
 */
public class ProcessOutput {
  private final StringBuilder myStdoutBuilder = new StringBuilder();
  private final StringBuilder myStderrBuilder = new StringBuilder();
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
