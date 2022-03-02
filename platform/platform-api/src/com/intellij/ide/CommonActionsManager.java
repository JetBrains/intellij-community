// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class CommonActionsManager {
  public static CommonActionsManager getInstance() {
    return ApplicationManager.getApplication().getService(CommonActionsManager.class);
  }

  public abstract AnAction createPrevOccurenceAction(OccurenceNavigator navigator);

  public abstract AnAction createNextOccurenceAction(OccurenceNavigator navigator);

  /**
   * @deprecated use {@link #createCollapseAllAction(TreeExpander, JComponent)} instead
   */
  @Deprecated(forRemoval = true)
  public abstract AnAction createExpandAllAction(TreeExpander expander);

  public abstract AnAction createExpandAllAction(TreeExpander expander, JComponent component);

  public abstract AnAction createExpandAllHeaderAction(TreeExpander expander, JComponent component);

  public abstract AnAction createExpandAllHeaderAction(JTree tree);

  /**
   * @deprecated use {@link #createCollapseAllAction(TreeExpander, JComponent)} instead
   */
  @Deprecated(forRemoval = true)
  public abstract AnAction createCollapseAllAction(TreeExpander expander);

  public abstract AnAction createCollapseAllAction(TreeExpander expander, JComponent component);

  public abstract AnAction createCollapseAllHeaderAction(TreeExpander expander, JComponent component);

  public abstract AnAction createCollapseAllHeaderAction(JTree tree);

  public abstract AnAction createHelpAction(String helpId);

  /**
   * Installs autoscroll capability support to JTree passed. Toggle action returned.
   *
   * @param project        current project
   * @param tree           should provide {@link CommonDataKeys#NAVIGATABLE} for handler to work on
   * @param optionProvider get/set API to externalizable property.
   * @return toggle action to be inserted to appropriate toolbar
   */
  public abstract AnAction installAutoscrollToSourceHandler(Project project, JTree tree, AutoScrollToSourceOptionProvider optionProvider);

  public abstract AnAction createExportToTextFileAction(@NotNull ExporterToTextFile exporter);
}
