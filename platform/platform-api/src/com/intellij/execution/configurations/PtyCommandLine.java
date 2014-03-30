/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.execution.configurations;

import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ArrayUtil;
import com.pty4j.PtyProcess;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * A flavor of GeneralCommandLine to start processes with Pseudo-Terminal (PTY).
 *
 * Note: this works only on Unix, on Windows regular processes are used instead.
 */
public class PtyCommandLine extends GeneralCommandLine {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.configurations.PtyCommandLine");

  public PtyCommandLine() { }

  @Override
  protected Process startProcess(@NotNull List<String> commands) throws IOException {
    if (SystemInfo.isUnix) {
      try {
        Map<String, String> env = Maps.newHashMap();
        setupEnvironment(env);

        if (isRedirectErrorStream()) {
          LOG.error("Launching process with PTY and redirected error stream is unsupported yet");
        }

        File workDirectory = getWorkDirectory();
        return PtyProcess.exec(ArrayUtil.toStringArray(commands), env, workDirectory != null ? workDirectory.getPath() : null, true);
      }
      catch (Throwable e) {
        LOG.error("Couldn't run process with PTY", e);
      }
    }

    return super.startProcess(commands);
  }
}
