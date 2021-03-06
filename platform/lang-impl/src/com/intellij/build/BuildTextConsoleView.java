// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build;

import com.intellij.build.events.*;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.LazyFileHyperlinkInfo;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;

import static com.intellij.util.ObjectUtils.chooseNotNull;

/**
 * @author Vladislav.Soroka
 */
public class BuildTextConsoleView extends ConsoleViewImpl implements BuildConsoleView, AnsiEscapeDecoder.ColoredTextAcceptor {
  private final AnsiEscapeDecoder myAnsiEscapeDecoder = new AnsiEscapeDecoder();

  public BuildTextConsoleView(@NotNull Project project, boolean viewer, @NotNull List<? extends Filter> executionFilters) {
    super(project, GlobalSearchScope.allScope(project), viewer, true);
    executionFilters.forEach(this::addMessageFilter);
  }

  @Override
  public void onEvent(@NotNull Object buildId, @NotNull BuildEvent event) {
    if (event instanceof BuildIssueEvent) {
      BuildConsoleUtils.print(this, ((BuildIssueEvent)event).getGroup(), ((BuildIssueEvent)event).getIssue());
    }
    else if (event instanceof FileMessageEvent) {
      boolean isStdOut = ((FileMessageEvent)event).getResult().getKind() != MessageEvent.Kind.ERROR;
      String description = event.getDescription();
      if (description != null) {
        append(description, isStdOut);
      }
      else {
        FilePosition position = ((FileMessageEvent)event).getFilePosition();
        StringBuilder fileLink = new StringBuilder();
        fileLink.append(position.getFile().getName());
        if (position.getStartLine() > 0) {
          fileLink.append(":").append(position.getStartLine() + 1);
        }
        if (position.getStartColumn() > 0) {
          fileLink.append(":").append(position.getStartColumn() + 1);
        }
        print(fileLink.toString(), ConsoleViewContentType.NORMAL_OUTPUT,
              new LazyFileHyperlinkInfo(getProject(), position.getFile().getPath(), position.getStartLine(), position.getStartColumn()));
        print(": ", ConsoleViewContentType.NORMAL_OUTPUT);
        append(event.getMessage(), isStdOut);
      }
    }
    else if (event instanceof MessageEvent) {
      appendEventResult(((MessageEvent)event).getResult());
    }
    else if (event instanceof FinishEvent) {
      appendEventResult(((FinishEvent)event).getResult());
    }
    else if (event instanceof OutputBuildEvent) {
      onEvent((OutputBuildEvent)event);
    }
    else {
      append(chooseNotNull(event.getDescription(), event.getMessage()), true);
    }
  }

  public void onEvent(@NotNull OutputBuildEvent event) {
    append(event.getMessage(), event.isStdOut());
  }

  public boolean appendEventResult(@Nullable EventResult eventResult) {
    if (eventResult == null) return false;
    boolean hasChanged = false;
    if (eventResult instanceof FailureResult) {
      List<? extends Failure> failures = ((FailureResult)eventResult).getFailures();
      if (failures.isEmpty()) return false;
      for (Iterator<? extends Failure> iterator = failures.iterator(); iterator.hasNext(); ) {
        Failure failure = iterator.next();
        if (append(failure)) {
          hasChanged = true;
        }
        if (iterator.hasNext()) {
          print("\n\n", ConsoleViewContentType.NORMAL_OUTPUT);
        }
      }
    }
    else if (eventResult instanceof MessageEventResult) {
      String details = ((MessageEventResult)eventResult).getDetails();
      if (details == null) {
        return false;
      }
      if (details.isEmpty()) {
        return false;
      }
      BuildConsoleUtils.printDetails(this, null, details);
      hasChanged = true;
    }
    return hasChanged;
  }

  public boolean append(@NotNull Failure failure) {
    String text = chooseNotNull(failure.getDescription(), failure.getMessage());
    if (text == null && failure.getError() != null) {
      text = failure.getError().getMessage();
    }
    if (text == null) return false;
    BuildConsoleUtils.printDetails(this, failure, text);
    return true;
  }

  public void append(@NotNull String text, boolean isStdOut) {
    Key outputType = !isStdOut ? ProcessOutputTypes.STDERR : ProcessOutputTypes.STDOUT;
    myAnsiEscapeDecoder.escapeText(text, outputType, this);
  }

  @Override
  public void coloredTextAvailable(@NotNull String text, @NotNull Key attributes) {
    print(text, ConsoleViewContentType.getConsoleViewType(attributes));
  }
}

