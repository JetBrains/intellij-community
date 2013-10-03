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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.KillableProcess;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

/**
 * @author Roman.Chernyatchik
 *         <p/>
 *         This process handler supports ANSI coloring and soft-kill feature. Soft kill works only on Unix.
 *         At first "stop" button send SIGINT signal to process, if it still hangs user can termintate it recursively with SIGKILL signal.
 *         <p/>
 *         P.S: probably OSProcessHandler is better place for this feature but it can affect other run configurations and should be tested
 */
public class KillableColoredProcessHandler extends ColoredProcessHandler implements KillableProcess {
  private boolean myShouldKillProcessSoftly = true;

  public KillableColoredProcessHandler(GeneralCommandLine commandLine) throws ExecutionException {
    super(commandLine);
  }

  public KillableColoredProcessHandler(final Process process, final String commandLine, @NotNull final Charset charset) {
    super(process, commandLine, charset);
  }

  public KillableColoredProcessHandler(final Process process, final String commandLine) {
    super(process, commandLine);
  }

  public void setShouldKillProcessSoftly(boolean shouldKillProcessSoftly) {
    myShouldKillProcessSoftly = shouldKillProcessSoftly;
  }

  /**
   * This method shouldn't be overridden, see shouldKillProcessSoftly
   *
   * @return
   */
  private boolean canKillProcessSoftly() {
    // soft-kill works on Unix systems
    return SystemInfo.isUnix && processCanBeKilledByOS(getProcess());
  }

  @Override
  protected void doDestroyProcess() {
    if (canKillProcessSoftly() && shouldKillProcessSoftly()) {
      // Unix: [soft-kill] at first send INT signal:
      final Process process = getProcess();
      if (UnixProcessManager.sendSigIntToProcessTree(process)) {
        return;
      }
    }

    // if soft kill isn't supported - use default implementation
    super.doDestroyProcess();
    // else IDE will suggest 'terminate dialog'

  }

  /**
   * This method should be overridden by children if the process shouldn't be killed softly (e.g. by kill -2)
   *
   * @return
   */
  protected boolean shouldKillProcessSoftly() {
    return myShouldKillProcessSoftly;
  }

  @Override
  public boolean canKillProcess() {
    return processCanBeKilledByOS(getProcess());
  }

  @Override
  public void killProcess() {
    // kill -9
    killProcessTree(getProcess());
  }
}
