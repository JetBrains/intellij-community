// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor;
import com.intellij.ide.actions.searcheverywhere.statistics.SearchFieldStatisticsCollector;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

/**
 * "Go to | File" action implementation.
 *
 * @author Eugene Belyaev
 * @author Constantine.Plotnikov
 */
public class GotoFileAction extends SearchEverywhereBaseAction implements DumbAware {
  public static final String ID = "GotoFile";

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    e = SearchFieldStatisticsCollector.wrapEventWithActionStartData(e);
    String tabID = FileSearchEverywhereContributor.class.getSimpleName();
    showInSearchEverywherePopup(tabID, e, true, true);
  }
}
