package com.intellij.ide.startupWizard;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ConfigImportHelper;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.wizard.WizardDialog;
import com.intellij.util.ImageLoader;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

/**
 * @author yole
 */
public class StartupWizard extends WizardDialog<StartupWizardModel> {
  public StartupWizard() {
    super(true, true, new StartupWizardModel());
    getPeer().setIconImage(ImageLoader.loadFromResource("/icon.png"));
  }

  @Override
  public void onWizardGoalAchieved() {
    super.onWizardGoalAchieved();
    final File path = myModel.getOldConfigPath();
    if (path != null) {
      ConfigImportHelper.doImport(PathManager.getConfigPath(), path);
    }
    
    try {
      PluginManager.saveDisabledPlugins(myModel.getDisabledPluginIds(), false);
    }
    catch (IOException e) {
      // ignore?
    }
    PluginManager.invalidatePlugins();
  }

  public static void run() {
    try {
      if (SystemInfo.isWindowsVista || SystemInfo.isMac) {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      }
    }
    catch (Exception e) {
      // ignore
    }
    new StartupWizard().show();    
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        StartupWizard.run();
      }
    });
  }

  @Override
  protected Dimension getWindowPreferredSize() {
    return new Dimension(600, 350);
  }
}
