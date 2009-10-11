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

import java.util.EventObject;

public class ProcessEvent extends EventObject{
  private String myText;
  private int myExitCode;

  public ProcessEvent(final ProcessHandler source) {
    super(source);
  }

  public ProcessEvent(final ProcessHandler source, final String text) {
    super(source);
    myText = text;
  }

  public ProcessEvent(final ProcessHandler source, final int exitCode) {
    super(source);
    myExitCode = exitCode;
  }

  public ProcessHandler getProcessHandler() {
    return (ProcessHandler)getSource();
  }

  public String getText() {
    return myText;
  }

  public int getExitCode() {
    return myExitCode;
  }
}