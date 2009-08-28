/**
 * @author Vladimir Kondratyev
 */
package com.intellij.ide.actions;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;

public class ViewStatusBarAction extends ToggleAction implements DumbAware {
  public boolean isSelected(AnActionEvent e){
    return UISettings.getInstance().SHOW_STATUS_BAR;
  }

  public void setSelected(AnActionEvent e,boolean state){
    UISettings uiSettings = UISettings.getInstance();
    uiSettings.SHOW_STATUS_BAR=state;
    uiSettings.fireUISettingsChanged();
  }
}
