/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author max
 */
public abstract class CommonActionsManager {
  public static CommonActionsManager getInstance() {
    return ServiceManager.getService(CommonActionsManager.class);
  }

  public abstract AnAction createPrevOccurenceAction(OccurenceNavigator navigator);

  public abstract AnAction createNextOccurenceAction(OccurenceNavigator navigator);

  @Deprecated
  public abstract AnAction createExpandAllAction(TreeExpander expander);

  public abstract AnAction createExpandAllAction(TreeExpander expander, JComponent component);

  public abstract AnAction createExpandAllHeaderAction(JTree tree);

  @Deprecated
  public abstract AnAction createCollapseAllAction(TreeExpander expander);

  public abstract AnAction createCollapseAllAction(TreeExpander expander, JComponent component);

  public abstract AnAction createCollapseAllHeaderAction(JTree tree);

  public abstract AnAction createHelpAction(String helpId);

  /**
   * Installs autoscroll capability support to JTree passed. Toggle action returned.
   *
   * @param project
   * @param tree           should provide DataConstants.NAVIGATABLE for handler to work on
   * @param optionProvider get/set API to externalizable property.
   * @return toggle action to be inserted to appropriate toolbar
   */
  public abstract AnAction installAutoscrollToSourceHandler(Project project, JTree tree, AutoScrollToSourceOptionProvider optionProvider);

  public abstract AnAction createExportToTextFileAction(@NotNull ExporterToTextFile exporter);
}
