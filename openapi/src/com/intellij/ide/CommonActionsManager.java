package com.intellij.ide;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 20, 2004
 * Time: 9:39:48 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class CommonActionsManager {
  public static CommonActionsManager getInstance() {
    return ApplicationManager.getApplication().getComponent(CommonActionsManager.class);
  }

  public abstract AnAction createPrevOccurenceAction(OccurenceNavigator navigator);
  public abstract AnAction createNextOccurenceAction(OccurenceNavigator navigator);

  public abstract AnAction createExpandAllAction(TreeExpander expander);
  public abstract AnAction createCollapseAllAction(TreeExpander expander);

  public abstract AnAction createHelpAction(String helpId);

  /**
   * Installs autoscroll capability support to JTree passed. Toggle action returned.
   * @param project
   * @return toggle action to be inserted to appropriate toolbar
   * @param tree should provide DataContstants.NAVIGATABLE for handler to work on
   * @param optionProvider get/set API to externalizable property.
   */
  public abstract AnAction installAutoscrollToSourceHandler(Project project, JTree tree, AutoScrollToSourceOptionProvider optionProvider);

  public abstract AnAction createExportToTextFileAction(ExporterToTextFile exporter);
}
