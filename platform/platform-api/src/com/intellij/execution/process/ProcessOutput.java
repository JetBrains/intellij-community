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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

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
    myExitCode = -1; // until set explicitly, exit code denotes an error.
  }

  public ProcessOutput(final int exitCode) {
    myExitCode = exitCode;
  }

  public void appendStdout(String text) {
    myStdoutBuilder.append(text);
  }

  public void appendStderr(String text) {
    myStderrBuilder.append(text);
  }

  public void setExitCode(int exitCode) {
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

  public void setTimeout() {
    myTimeout = true;
  }

  public boolean isTimeout() {
    return myTimeout;
  }

  public List<String> getStdoutLines() {
    return splitLines(getStdout());
  }

  public List<String> getStderrLines() {
    return splitLines(getStderr());
  }

  private static List<String> splitLines(String s) {
    String converted = StringUtil.convertLineSeparators(s);
    return StringUtil.split(converted, "\n");
  }

  /**
   * If exit code is nonzero or the process timed out, logs stderr and exit code and returns false,
   * else just returns true.
   * @param logger where to put error information
   * @return true iff exit code is zero
   */
  public boolean checkSuccess(@NotNull final Logger logger) {
    if (getExitCode() != 0 || isTimeout()) {
      logger.info(getStderr() + (isTimeout()? "\nTimed out" : "\nExit code " + getExitCode()));
      return false;
    }
    return true;
  }
}
