/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

/**
 * @author Roman.Chernyatchik, oleg
 * @deprecated Use GeneralCommandLine instead
 * @deprecated Usages only in Ruby. Move to Ruby module?
 */
@Deprecated
public class CommandLineArgumentsProvider {
    /**
   * @return Commands to execute (one command corresponds to one add argument)
   */
  public String[] getArguments() { return ArrayUtil.EMPTY_STRING_ARRAY; }

  public boolean passParentEnvs() { return false; }

  @Nullable
  public Map<String, String> getAdditionalEnvs() { return Collections.emptyMap(); }


  public String getCommandLineString() {
    return toCommandLine(getArguments());
  }

  public static String toCommandLine(String... commands) {
    if (commands.length > 0) {
      commands[0] = FileUtil.toSystemDependentName(commands[0]);
      return StringUtil.join(commands, " ");
    }
    return "";
  }
}
