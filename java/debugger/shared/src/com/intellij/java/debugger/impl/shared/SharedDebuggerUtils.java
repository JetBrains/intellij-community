// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.shared;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.impl.RunnerContentUi;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.content.Content;
import com.intellij.ui.viewModel.extraction.ToolWindowContentExtractor;
import com.intellij.unscramble.MergeableDumpItem;
import com.intellij.unscramble.ThreadDumpPanel;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public final class SharedDebuggerUtils {
  /**
   * @see com.intellij.debugger.engine.DebuggerUtils#translateStringValue(String)
   */
  public static String translateStringValue(final String str) {
    int length = str.length();
    final StringBuilder buffer = new StringBuilder();
    StringUtil.escapeStringCharacters(length, str, buffer);
    return buffer.toString();
  }

  private static int myThreadDumpsCount = 0;

  @ApiStatus.Internal
  public static ThreadDumpPanel createThreadDumpPanel(Project project, RunnerLayoutUi ui, List<Filter> filters) {
    return createThreadDumpPanel(project, Collections.emptyList(), ui, filters);
  }

  @ApiStatus.Internal
  public static ThreadDumpPanel createThreadDumpPanel(Project project,
                                                      List<MergeableDumpItem> dumpItems,
                                                      RunnerLayoutUi ui,
                                                      List<Filter> filters) {
    final TextConsoleBuilder consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
    consoleBuilder.filters(filters);
    final ConsoleView consoleView = consoleBuilder.getConsole();
    final DefaultActionGroup toolbarActions = new DefaultActionGroup();
    consoleView.allowHeavyFilters();
    final ThreadDumpPanel panel = ThreadDumpPanel.createFromDumpItems(project, consoleView, toolbarActions, dumpItems);

    String id = JavaDebuggerSharedBundle.message("thread.dump.name", DateFormatUtil.formatTimeWithSeconds(System.currentTimeMillis()));
    final Content content = ui.createContent(id + " " + myThreadDumpsCount, panel, id, null, null);
    content.putUserData(RunnerContentUi.LIGHTWEIGHT_CONTENT_MARKER, Boolean.TRUE);
    content.setCloseable(true);
    content.setDescription(JavaDebuggerSharedBundle.message("thread.dump"));
    content.putUserData(ToolWindowContentExtractor.SYNC_TAB_TO_REMOTE_CLIENTS, true);
    ui.addContent(content);
    ui.selectAndFocus(content, true, true);
    myThreadDumpsCount++;
    Disposer.register(content, consoleView);
    ui.selectAndFocus(content, true, false);
    if (!dumpItems.isEmpty()) {
      panel.selectStackFrame(0);
    }
    return panel;
  }
}
