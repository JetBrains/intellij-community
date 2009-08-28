package com.intellij.ide.actions;

import com.intellij.ide.ui.customization.CustomizationConfigurable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;

/**
 * @author yole
 */
public class CustomizeUIAction extends AnAction {
  public CustomizeUIAction() {
    super("Customize Menus and Toolbars...");
  }

  public void actionPerformed(AnActionEvent e) {
    ShowSettingsUtilImpl.getInstance().editConfigurable(e.getData(PlatformDataKeys.PROJECT), CustomizationConfigurable.getInstance());
  }
}
