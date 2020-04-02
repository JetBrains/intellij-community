// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl.attach;

import com.intellij.execution.filters.ConsoleFilterProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaDebuggerAttachFilterProvider implements ConsoleFilterProvider {
  @Override
  public Filter @NotNull [] getDefaultFilters(@NotNull Project project) {
    return new Filter[]{new JavaDebuggerAttachFilter()};
  }

  private static class JavaDebuggerAttachFilter implements Filter {
    static final Pattern PATTERN = Pattern.compile("Listening for transport (\\S+) at address: (\\S+)");

    @Override
    public @Nullable Result applyFilter(@NotNull String line, int entireLength) {
      Matcher matcher = PATTERN.matcher(line);
      if (!matcher.find()) {
        return null;
      }
      String transport = matcher.group(1);
      String address = matcher.group(2);
      int start = entireLength - line.length();

      return new Result(start + matcher.start(), start + matcher.end(),
                        project -> JavaAttachDebuggerProvider.attach(transport, address, project));
    }
  }
}
