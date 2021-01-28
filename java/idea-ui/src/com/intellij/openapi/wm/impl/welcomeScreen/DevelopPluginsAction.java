// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class DevelopPluginsAction extends AnAction implements DumbAware {
  @NonNls private static final String PLUGIN_WEBSITE = "https://plugins.jetbrains.com/docs/intellij/";

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    try {
      BrowserUtil.browse(PLUGIN_WEBSITE);
    }
    catch(IllegalStateException ex) {
      // ignore
    }
  }
}