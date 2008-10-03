package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;

/**
 * @author yole
 */
public class ActionPreloader {
  public ActionPreloader(StartupManager manager) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      manager.registerPostStartupActivity(new Runnable() {
        public void run() {
          ((ActionManagerImpl)ActionManager.getInstance()).preloadActions();
        }
      });
    }
  }
}
