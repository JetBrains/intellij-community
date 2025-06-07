// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.statistics;

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereMlService;
import com.intellij.internal.statistic.utils.StartMoment;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomizedDataContext;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.time.Duration;

import static com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector.*;

@ApiStatus.Internal
public final class SearchFieldStatisticsCollector implements Disposable {

  private final Project myProject;
  private final JTextField myTextField;
  private final StartMoment myStartMoment;
  private final SearchEverywhereMlService myMlService;
  private final SearchPerformanceTracker myPerformanceTracker;

  private int mySymbolKeysTyped;
  private int myNavKeysTyped;

  private SearchFieldStatisticsCollector(JTextField field, SearchPerformanceTracker performanceTracker,
                                         SearchEverywhereMlService mlService, Project project, @Nullable StartMoment startMoment) {
    myProject = project;
    myPerformanceTracker = performanceTracker;
    myMlService = mlService;
    myTextField = field;
    myStartMoment = startMoment;
  }

  public static SearchFieldStatisticsCollector createAndStart(JTextField field,
                                                              SearchPerformanceTracker performanceTracker,
                                                              SearchEverywhereMlService mlService,
                                                              Project project,
                                                              @Nullable StartMoment startMoment) {
    SearchFieldStatisticsCollector res = new SearchFieldStatisticsCollector(field, performanceTracker, mlService, project, startMoment);
    res.initListeners();
    return res;
  }

  @Override
  public void dispose() {
    SearchSessionPerformanceInfo info = myPerformanceTracker.getPerformanceInfo();
    SESSION_FINISHED.log(myProject, pairs -> {
      FinishedSearchPerformanceInfo firstSearch = info.getFirstSearch();
      if (firstSearch != null) {
        pairs.add(FIRST_TAB_ID.with(firstSearch.getTab()));
        pairs.add(TIME_TO_FIRST_RESULT.with(firstSearch.getTimeToFirstResult()));
        Duration fromTheStartMoment = firstSearch.getDurationToFirstResultFromTheStartMoment();
        if (fromTheStartMoment != null) {
          pairs.add(DURATION_TO_FIRST_RESULT_FROM_ACTION_START_MS.with(fromTheStartMoment.toMillis()));
        }
      }

      FinishedSearchPerformanceInfo lastSearch = info.getLastSearch();
      if (lastSearch != null) {
        pairs.add(LAST_TAB_ID.with(lastSearch.getTab()));
        pairs.add(TIME_TO_FIRST_RESULT_LAST_QUERY.with(lastSearch.getTimeToFirstResult()));
        Duration fromTheStartMoment = lastSearch.getDurationToFirstResultFromTheStartMoment();
        if (fromTheStartMoment != null) {
          pairs.add(DURATION_TO_FIRST_RESULT_LAST_QUERY_FROM_ACTION_START_MS.with(fromTheStartMoment.toMillis()));
        }
      }

      pairs.add(TYPED_NAVIGATION_KEYS.with(myNavKeysTyped));
      pairs.add(TYPED_SYMBOL_KEYS.with(mySymbolKeysTyped));
      pairs.add(DURATION_MS.with(info.getDuration()));
      if (myStartMoment != null) {
        pairs.add(DURATION_FROM_ACTION_START_MS.with(myStartMoment.getCurrentDuration().toMillis()));
      }
      pairs.add(DIALOG_WAS_CANCELLED.with(myPerformanceTracker.isDialogCancelled()));

      if (myMlService != null) {
        pairs.add(ML_EXPERIMENT_VERSION.with(myMlService.getExperimentVersion()));
        pairs.add(ML_EXPERIMENT_GROUP.with(myMlService.getExperimentGroup()));
      }
    });
  }

  private void initListeners() {
    myTextField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        mySymbolKeysTyped++;
      }

      @Override
      public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_UP || code == KeyEvent.VK_KP_UP || code == KeyEvent.VK_PAGE_UP
            || code == KeyEvent.VK_DOWN || code == KeyEvent.VK_KP_DOWN || code == KeyEvent.VK_PAGE_DOWN) {
          myNavKeysTyped++;
        }
      }
    });
  }

  private static final DataKey<StartMoment> START_MOMENT_KEY = DataKey.create("start_moment_of_search_everywhere");

  public static StartMoment getStartMoment(AnActionEvent event) {
    return event.getData(START_MOMENT_KEY);
  }

  public static @NotNull AnActionEvent wrapEventWithActionStartData(@NotNull AnActionEvent event) {
    StartMoment startMoment = StartMoment.Companion.now();
    DataContext initialDataContext = event.getDataContext();
    DataContext wrappedDataContext = wrapDataContextWithActionStartData(initialDataContext, startMoment);
    if (wrappedDataContext == initialDataContext) return event;
    return event.withDataContext(wrappedDataContext);
  }

  private static @NotNull DataContext wrapDataContextWithActionStartData(@NotNull DataContext dataContext, @NotNull StartMoment startMoment) {
    if (dataContext.getData(START_MOMENT_KEY) != null) return dataContext;
    return CustomizedDataContext.withSnapshot(dataContext, sink -> sink.set(START_MOMENT_KEY, startMoment));
  }

  public static @NotNull DataContext wrapDataContextWithActionStartData(@NotNull DataContext dataContext) {
    return wrapDataContextWithActionStartData(dataContext, StartMoment.Companion.now());
  }
}
