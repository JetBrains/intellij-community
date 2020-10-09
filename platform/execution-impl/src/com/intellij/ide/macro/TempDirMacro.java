/*
 * Copyright 2000-2020 JetBrains s.r.o.
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
package com.intellij.ide.macro;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TempDirMacro extends Macro {
  @NotNull
  @Override
  public String getName() {
    return "TempDir";
  }

  @NotNull
  @Override
  public String getDescription() {
    return IdeBundle.message("macro.temp.dir");
  }

  @Nullable
  @Override
  public String expand(@NotNull DataContext dataContext) {
    if (SystemInfo.isWindows) {
      String tempDir = System.getenv("TEMP");
      if (tempDir == null) {
        String homeDir = System.getProperty("user.home");
        if (homeDir != null) {
          tempDir = homeDir + "\\AppData\\Local\\Temp";
        }
      }
      return tempDir;
    }

    return "/tmp";
  }
}
