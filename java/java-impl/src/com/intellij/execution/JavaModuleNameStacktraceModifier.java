// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.console.ConsoleLineModifier;
import com.intellij.execution.filters.ExceptionWorker;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaModuleNameStacktraceModifier implements ConsoleLineModifier {
  @Override
  public @Nullable String modify(@NotNull String line) {
    int nonWhitespaceIdx = StringUtil.skipWhitespaceForward(line, 0);
    if (nonWhitespaceIdx < line.length() && line.startsWith("at ", nonWhitespaceIdx)) {
      ExceptionWorker.ParsedLine parsedLine = ExceptionWorker.parseExceptionLine(line);
      if (parsedLine != null) {
        int startOffset = parsedLine.classFqnRange.getStartOffset();
        if (line.substring(0, startOffset).contains("/")) {
          return "at " + line.substring(startOffset);
        }
      }
    }
    return null;
  }
}
