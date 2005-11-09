/*
 * Copyright (c) 2005 JetBrains. All Rights Reserved.
 */
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ide.ui.UISettings;

public class ViewNavigationBarAction extends ToggleAction {
  public boolean isSelected(AnActionEvent e){
    return UISettings.getInstance().SHOW_NAVIGATION_BAR;
  }

  public void setSelected(AnActionEvent e,boolean state){
    UISettings uiSettings = UISettings.getInstance();
    uiSettings.SHOW_NAVIGATION_BAR=state;
    uiSettings.fireUISettingsChanged();
  }
}
