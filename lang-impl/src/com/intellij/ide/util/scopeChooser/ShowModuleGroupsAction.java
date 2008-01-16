/*
 * User: anna
 * Date: 16-Jan-2008
 */
package com.intellij.ide.util.scopeChooser;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.util.IconLoader;
import com.intellij.packageDependencies.DependencyUISettings;

public class ShowModuleGroupsAction extends ToggleAction {
  private final Runnable myUpdate;

  public ShowModuleGroupsAction(final Runnable update) {
    super("Show module groups",
          "Show/hide module groups", IconLoader.getIcon("/nodes/moduleGroupClosed.png"));
    myUpdate = update;
  }

  public boolean isSelected(AnActionEvent event) {
    return DependencyUISettings.getInstance().UI_SHOW_MODULE_GROUPS;
  }

  public void setSelected(AnActionEvent event, boolean flag) {
    DependencyUISettings.getInstance().UI_SHOW_MODULE_GROUPS = flag;
    myUpdate.run();
  }

  public void update(final AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(DependencyUISettings.getInstance().UI_SHOW_MODULES);
  }
}