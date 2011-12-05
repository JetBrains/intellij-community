/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.execution.KillableProcess;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * @author Roman.Chernyatchik
 *
 * This process handler supports ANSI coloring and soft-kill feature. Soft kill works only on Unix.
 * At first "stop" button send SIGINT signal to process, if it still hangs user can termintate it recursively with SIGKILL signal.
 *
 * P.S: probably OSProcessHandler is better place for this feature but it can affect other run configurations and should be tested
 */
public class KillableColoredProcessHandler extends ColoredProcessHandler implements KillableProcess {
  public KillableColoredProcessHandler(final Process process, final String commandLine, @Nullable final Charset charset) {
    super(process, commandLine, charset);
  }

  public KillableColoredProcessHandler(final Process process, final String commandLine) {
    super(process, commandLine);
  }

  @Override
  public boolean canKillSoftly() {
    // soft-kill works on Unix systems
    return SystemInfo.isUnix;
  }

  @Override
  protected void doDestroyProcess() {
    if (!canKillSoftly()) {
      // if soft kill isn't supported - use default implementation
      super.doDestroyProcess();
      return;
    }

    // Unix: [soft-kill] at first send INT signal:
    final Process process = getProcess();
    UnixProcessManager.sendSigIntToProcessTree(process);

    // else IDE will suggest 'terminate dialog'
  }

  @Override
  public void killProcess() {
    // kill -9
    killProcessTree(getProcess());
  }
}
