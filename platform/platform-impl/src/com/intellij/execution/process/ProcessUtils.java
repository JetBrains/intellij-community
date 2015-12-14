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
import org.jetbrains.annotations.Nullable;

import java.io.*;
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


  public static IProcessList getProcessList(String helpersRoot) {
    if (SystemInfo.isWindows) {
      return new ProcessListWin32(helpersRoot);
    }
    if (SystemInfo.isLinux) {
      return new ProcessListLinux();
    }
    if (SystemInfo.isMac) {
      return new ProcessListMac();
    }

    LOG.error("Unexpected platform. Unable to list processes.");
    return new IProcessList() {

      @Override
      public ProcessInfo[] getProcessList() {
        return new ProcessInfo[0];
      }
    };
  }

  @NotNull
  public static char[] loadFileText(@NotNull File file, @Nullable String encoding) throws IOException {
    InputStream stream = new FileInputStream(file);
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    Reader reader = encoding == null ? new InputStreamReader(stream) : new InputStreamReader(stream, encoding);
    try {
      return loadText(reader);
    }
    finally {
      reader.close();
    }
  }

  @NotNull
  public static char[] loadText(@NotNull Reader reader) throws IOException {
    //fill a buffer with the contents
    int BUFFER_SIZE = 2 * 1024;

    char[] readBuffer = new char[BUFFER_SIZE];
    int n = reader.read(readBuffer);

    int DEFAULT_FILE_SIZE = 8 * BUFFER_SIZE;

    StringBuffer buffer = new StringBuffer(DEFAULT_FILE_SIZE);

    while (n > 0) {
      buffer.append(readBuffer, 0, n);
      n = reader.read(readBuffer);
    }

    return buffer.toString().toCharArray();
  }
}
