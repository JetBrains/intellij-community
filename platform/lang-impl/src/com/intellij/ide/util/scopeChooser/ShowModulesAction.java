/*
 * User: anna
 * Date: 16-Jan-2008
 */
package com.intellij.ide.util.scopeChooser;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.util.IconLoader;
import com.intellij.packageDependencies.DependencyUISettings;

public final class ShowModulesAction extends ToggleAction {
  private final Runnable myUpdate;

  public ShowModulesAction(final Runnable update) {
    super(IdeBundle.message("action.show.modules"),
          IdeBundle.message("action.description.show.modules"), IconLoader.getIcon("/objectBrowser/showModules.png"));
    myUpdate = update;
  }

  public boolean isSelected(AnActionEvent event) {
    return DependencyUISettings.getInstance().UI_SHOW_MODULES;
  }

  public void setSelected(AnActionEvent event, boolean flag) {
    DependencyUISettings.getInstance().UI_SHOW_MODULES = flag;
    myUpdate.run();
  }
}