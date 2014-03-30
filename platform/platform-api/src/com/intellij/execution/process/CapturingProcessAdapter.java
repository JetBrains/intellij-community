/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class CapturingProcessAdapter extends ProcessAdapter {
  private final ProcessOutput myOutput;

  public CapturingProcessAdapter(ProcessOutput output) {
    myOutput = output;
  }

  public CapturingProcessAdapter() {
    this(new ProcessOutput());
  }

  @Override
  public void onTextAvailable(ProcessEvent event, Key outputType) {
    addToOutput(event.getText(), outputType);
  }

  protected void addToOutput(String text, Key outputType) {
    if (outputType == ProcessOutputTypes.STDOUT) {
      myOutput.appendStdout(text);
    }
    if (outputType == ProcessOutputTypes.STDERR) {
      myOutput.appendStderr(text);
    }
  }

  @Override
  public void processTerminated(@NotNull final ProcessEvent event) {
    myOutput.setExitCode(event.getExitCode());
  }

  public ProcessOutput getOutput() {
    return myOutput;
  }
}
