/*
 * User: anna
 * Date: 16-Jan-2008
 */
package com.intellij.ide.util.scopeChooser;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.packageDependencies.DependencyUISettings;
import com.intellij.util.Icons;

public final class FlattenPackagesAction extends ToggleAction {
  private final Runnable myUpdate;

  public FlattenPackagesAction(Runnable update) {
    super(IdeBundle.message("action.flatten.packages"),
          IdeBundle.message("action.flatten.packages"), Icons.FLATTEN_PACKAGES_ICON);
    myUpdate = update;
  }

  public boolean isSelected(AnActionEvent event) {
    return DependencyUISettings.getInstance().UI_FLATTEN_PACKAGES;
  }

  public void setSelected(AnActionEvent event, boolean flag) {
    DependencyUISettings.getInstance().UI_FLATTEN_PACKAGES = flag;
    myUpdate.run();
  }
}