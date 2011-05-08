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
import com.intellij.openapi.util.Key;

import java.nio.charset.Charset;

/**
 * Utility class for running an external process and capturing its standard output and error streams.
 *
 * @author yole
 */
public class CapturingProcessHandler extends OSProcessHandler {
  private static final Logger LOG = Logger.getInstance(CapturingProcessHandler.class);
  private final Charset myCharset;
  private final ProcessOutput myOutput = new ProcessOutput();

  public CapturingProcessHandler(final Process process) {
    this(process, null, "");
  }

  public CapturingProcessHandler(final Process process, final Charset charset) {
    this(process, charset, "");
  }

  public CapturingProcessHandler(final Process process, final Charset charset, final String commandLine) {
    super(process, commandLine);
    myCharset = charset;
    addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        if (outputType == ProcessOutputTypes.STDOUT) {
          myOutput.appendStdout(event.getText());
        }
        if (outputType == ProcessOutputTypes.STDERR) {
          myOutput.appendStderr(event.getText());
        }
      }
    });
  }

  public ProcessOutput runProcess() {
    startNotify();
    if (waitFor()) {
      myOutput.setExitCode(getProcess().exitValue());
    } else {
      LOG.info("runProcess: exit value unavailable");
    }
    return myOutput;
  }

  public ProcessOutput runProcess(int timeoutInMilliseconds) {
    if (timeoutInMilliseconds < 0) {
      return runProcess();
    }
    startNotify();
    if (waitFor(timeoutInMilliseconds)) {
      myOutput.setExitCode(getProcess().exitValue());
    }
    else {
      destroyProcess();
      myOutput.setTimeout();
    }
    return myOutput;
  }

  @Override
  public Charset getCharset() {
    if (myCharset != null) {
      return myCharset;
    }
    return super.getCharset();
  }
}
