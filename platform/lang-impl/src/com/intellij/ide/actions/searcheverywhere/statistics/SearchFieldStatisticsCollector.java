// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.statistics;

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereMlService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import static com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector.*;

public final class SearchFieldStatisticsCollector implements Disposable {

  private final Project myProject;
  private final JTextField myTextField;
  private final SearchEverywhereMlService myMlService;
  private final SearchPerformanceTracker myPerformanceTracker;

  private int mySymbolKeysTyped;
  private int myNavKeysTyped;

  private SearchFieldStatisticsCollector(JTextField field, SearchPerformanceTracker performanceTracker,
                                         SearchEverywhereMlService mlService, Project project) {
    myProject = project;
    myPerformanceTracker = performanceTracker;
    myMlService = mlService;
    myTextField = field;
  }

  public static SearchFieldStatisticsCollector createAndStart(JTextField field,
                                                              SearchPerformanceTracker performanceTracker,
                                                              SearchEverywhereMlService mlService,
                                                              Project project) {
    SearchFieldStatisticsCollector res = new SearchFieldStatisticsCollector(field, performanceTracker, mlService, project);
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
      }

      FinishedSearchPerformanceInfo lastSearch = info.getLastSearch();
      if (lastSearch != null) {
        pairs.add(LAST_TAB_ID.with(lastSearch.getTab()));
        pairs.add(TIME_TO_FIRST_RESULT_LAST_QUERY.with(lastSearch.getTimeToFirstResult()));
      }

      pairs.add(TYPED_NAVIGATION_KEYS.with(myNavKeysTyped));
      pairs.add(TYPED_SYMBOL_KEYS.with(mySymbolKeysTyped));
      pairs.add(DURATION_MS.with(info.getDuration()));

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
}
