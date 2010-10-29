/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Roman.Chernyatchik, oleg
 */
public abstract class CommandLineArgumentsProvider {
    /**
   * @return Commands to execute (one command corresponds to one add argument)
   */
  public abstract String[] getArguments();

  public abstract boolean passParentEnvs();

  @Nullable
  public abstract Map<String, String> getAdditionalEnvs();


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
