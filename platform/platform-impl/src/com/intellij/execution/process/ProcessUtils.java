/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/******************************************************************************
 * Copyright (C) 2013  Fabio Zadrozny
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Fabio Zadrozny <fabiofz@gmail.com> - initial API and implementation
 ******************************************************************************/
package com.intellij.execution.process;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class ProcessUtils {
  private static final Logger LOG = Logger.getInstance(ProcessUtils.class);

  /**
   * Passes the commands directly to Runtime.exec (with the passed envp)
   */
  public static Process createProcess(String[] cmdarray, String[] envp, File workingDir) throws IOException {
    return Runtime.getRuntime().exec(getWithoutEmptyParams(cmdarray), getWithoutEmptyParams(envp), workingDir);
  }

  /**
   * @return a new array without any null/empty elements originally contained in the array.
   */
  private static String[] getWithoutEmptyParams(String[] cmdarray) {
    if (cmdarray == null) {
      return null;
    }
    ArrayList<String> list = new ArrayList<String>();
    for (String string : cmdarray) {
      if (string != null && string.length() > 0) {
        list.add(string);
      }
    }
    return list.toArray(new String[list.size()]);
  }


  @NotNull
  public static ProcessInfo[] getProcessList() {
    if (SystemInfo.isWindows) {
      return ProcessListWin32.getProcessList();
    }
    if (SystemInfo.isLinux || SystemInfo.isMac) {
      return ProcessListLinux.getProcessList(SystemInfo.isMac);
    }
    LOG.error("Unexpected platform. Unable to list processes.");
    return new ProcessInfo[0];
  }
}
