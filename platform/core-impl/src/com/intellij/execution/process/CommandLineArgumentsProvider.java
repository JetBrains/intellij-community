// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

/**
 * @author Roman.Chernyatchik, oleg
 * @deprecated Use {@link com.intellij.execution.configurations.GeneralCommandLine} instead
 * @deprecated Usages only in Ruby. Move to Ruby module?
 */
@Deprecated
public class CommandLineArgumentsProvider {
    /**
   * @return Commands to execute (one command corresponds to one add argument)
   */
  @NotNull
  public String[] getArguments() { return ArrayUtilRt.EMPTY_STRING_ARRAY; }

  public boolean passParentEnvs() { return false; }

  @Nullable
  public Map<String, String> getAdditionalEnvs() { return Collections.emptyMap(); }


  public String getCommandLineString() {
    String[] commands = getArguments();
    if (commands.length > 0) {
      commands[0] = FileUtil.toSystemDependentName(commands[0]);
      return StringUtil.join(commands, " ");
    }
    return "";
  }
}
