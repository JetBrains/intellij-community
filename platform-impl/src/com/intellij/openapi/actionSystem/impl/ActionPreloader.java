package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.actionSystem.ActionManager;

/**
 * @author yole
 */
public class ActionPreloader {
  public ActionPreloader(StartupManager manager) {
    manager.registerPostStartupActivity(new Runnable() {
      public void run() {
        ((ActionManagerImpl)ActionManager.getInstance()).preloadActions();
      }
    });
  }
}
