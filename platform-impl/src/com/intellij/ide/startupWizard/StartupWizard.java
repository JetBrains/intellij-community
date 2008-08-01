package com.intellij.ide.startupWizard;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.ui.wizard.WizardDialog;
import com.intellij.util.ImageLoader;

import javax.swing.*;
import java.awt.*;
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

    try {
      PluginManager.saveDisabledPlugins(myModel.getDisabledPluginIds(), false);
    }
    catch (IOException e) {
      // ignore?
    }
    PluginManager.invalidatePlugins();
  }

  public static void run() {
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
