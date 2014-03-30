/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NonNls;

import java.io.File;

/**
 * @author yole
 */
public class DevelopPluginsAction extends AnAction implements DumbAware {
  @NonNls private static final String PLUGIN_URL = PathManager.getHomePath() + "/Plugin Development Readme.html";
  @NonNls private static final String PLUGIN_WEBSITE = "http://www.jetbrains.com/idea/plugins/plugin_developers.html";

  @Override
  public void actionPerformed(final AnActionEvent e) {
    try {
      if (new File(PLUGIN_URL).isFile()) {
        BrowserUtil.browse(PLUGIN_URL);
      }
      else {
        BrowserUtil.browse(PLUGIN_WEBSITE);
      }
    }
    catch(IllegalStateException ex) {
      // ignore
    }
  }
}