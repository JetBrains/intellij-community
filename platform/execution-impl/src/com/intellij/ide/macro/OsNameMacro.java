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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public class OsNameMacro extends Macro {
  @NotNull
  @Override
  public String getName() {
    return "OSName";
  }

  @NotNull
  @Override
  public String getDescription() {
    return IdeBundle.message("macro.os.name");
  }

  @Nullable
  @Override
  public String expand(@NotNull DataContext dataContext) {
    String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
    int firstSpace = osName.indexOf(' ');
    return firstSpace < 0 ? osName : osName.substring(0, firstSpace);
  }
}
