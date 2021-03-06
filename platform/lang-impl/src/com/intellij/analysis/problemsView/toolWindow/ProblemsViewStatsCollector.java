// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView.toolWindow;

import com.intellij.analysis.problemsView.Problem;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.ToggleOptionAction;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;

final class ProblemsViewStatsCollector extends CounterUsagesCollector {
  private static final String UNKNOWN = "unknown";
  private static final List<String> TABS = List.of("CurrentFile", "ProjectErrors", UNKNOWN);

  private static final EventField<String> TAB_NAME = EventFields.String("scope_tab", TABS);
  private static final EventField<Integer> PROBLEMS_COUNT = EventFields.Int("problems_count");
  private static final EventField<Boolean> PREVIEW_ENABLED = EventFields.Boolean("preview");
  private static final EventField<Long> DURATION = EventFields.Long("duration_seconds");
  private static final EventField<Integer> PROBLEM_SEVERITY = EventFields.Int("severity");

  private static final EventLogGroup PROBLEMS_VIEW_GROUP = new EventLogGroup("problems.view.sessions", 1);
  private static final VarargEventId TAB_SHOWN = PROBLEMS_VIEW_GROUP.registerVarargEvent(
    "problems.tab.shown", TAB_NAME, PROBLEMS_COUNT, PREVIEW_ENABLED);
  private static final VarargEventId TAB_HIDDEN = PROBLEMS_VIEW_GROUP.registerVarargEvent(
    "problems.tab.hidden", TAB_NAME, PROBLEMS_COUNT, PREVIEW_ENABLED, DURATION);
  private static final VarargEventId PROBLEM_SELECTED = PROBLEMS_VIEW_GROUP.registerVarargEvent(
    "select.item", TAB_NAME, PROBLEM_SEVERITY);

  private static @NotNull String tabName(@NotNull ProblemsViewPanel panel) {
    ToolWindow window = ProblemsView.getToolWindow(panel.getProject());
    if (window == null) return UNKNOWN;
    ContentManager manager = window.getContentManagerIfCreated();
    if (manager == null) return UNKNOWN;
    Content content = manager.getContent(panel);
    if (content == null) return UNKNOWN;
    int index = manager.getIndexOfContent(content);
    return 0 <= index && index < TABS.size() ? TABS.get(index) : UNKNOWN;
  }

  private static boolean previewEnabled(@NotNull ProblemsViewPanel panel) {
    ToggleOptionAction.Option option = panel.getShowPreview();
    return option != null && option.isSelected();
  }

  private static int problemsCount(@NotNull ProblemsViewPanel panel) {
    Root root = panel.getTreeModel().getRoot();
    return root == null ? 0 : root.getProblemCount();
  }

  private static int problemSeverity(@NotNull Problem problem) {
    HighlightingProblem highlighting = problem instanceof HighlightingProblem ? (HighlightingProblem)problem : null;
    return highlighting != null ? highlighting.getSeverity() : HighlightSeverity.ERROR.myVal;
  }

  static void tabShown(@NotNull ProblemsViewPanel panel) {
    TAB_SHOWN.log(panel.getProject(),
                  TAB_NAME.with(tabName(panel)),
                  PROBLEMS_COUNT.with(problemsCount(panel)),
                  PREVIEW_ENABLED.with(previewEnabled(panel)));
  }

  static void tabHidden(@NotNull ProblemsViewPanel panel, long nano) {
    TAB_HIDDEN.log(panel.getProject(),
                   TAB_NAME.with(tabName(panel)),
                   PROBLEMS_COUNT.with(problemsCount(panel)),
                   PREVIEW_ENABLED.with(previewEnabled(panel)),
                   DURATION.with(TimeUnit.NANOSECONDS.toSeconds(nano)));
  }

  static void problemSelected(@NotNull ProblemsViewPanel panel, @NotNull Problem problem) {
    PROBLEM_SELECTED.log(panel.getProject(),
                         TAB_NAME.with(tabName(panel)),
                         PROBLEM_SEVERITY.with(problemSeverity(problem)));
  }

  @Override
  public EventLogGroup getGroup() {
    return PROBLEMS_VIEW_GROUP;
  }
}
