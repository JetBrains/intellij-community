// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build;

import com.intellij.build.events.BuildEvent;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public final class BuildTextConsoleView extends ConsoleViewImpl implements BuildConsoleView {

  private final AnsiEscapeDecoder myAnsiEscapeDecoder = new AnsiEscapeDecoder();

  public BuildTextConsoleView(@NotNull Project project, boolean viewer, @NotNull List<? extends Filter> executionFilters) {
    super(project, GlobalSearchScope.allScope(project), viewer, true);
    executionFilters.forEach(this::addMessageFilter);
  }

  @Override
  public void onEvent(@NotNull Object buildId, @NotNull BuildEvent event) { }

  /**
   * @deprecated Use the {@link ConsoleViewImpl#print} function instead
   */
  @Deprecated
  public void append(@NotNull String text, boolean isStdOut) {
    print(text, isStdOut ? ProcessOutputType.STDOUT : ProcessOutputType.STDERR);
  }

  public void print(@NotNull String text, @NotNull Key<?> outputType) {
    myAnsiEscapeDecoder.escapeText(text, outputType, (decodedText, attributes) ->
      print(decodedText, ConsoleViewContentType.getConsoleViewType(attributes))
    );
  }

  @ApiStatus.Internal
  public static void print(@NotNull ConsoleView consoleView, @NotNull String text, @NotNull Key<?> outputType) {
    if (consoleView instanceof BuildTextConsoleView buildTextConsoleView) {
      // Route through the ANSI-decoding print so that escape sequences in the build output are converted into text
      // attributes instead of leaking into the console document (mirrors the old per-node console).
      buildTextConsoleView.print(text, outputType);
    }
    else {
      consoleView.print(text, ConsoleViewContentType.getConsoleViewType(outputType));
    }
  }
}

