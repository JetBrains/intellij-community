/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.project.DumbAware;

public class JetBrainsTvAction extends AnAction implements DumbAware {
  private final String myUrl;

  public JetBrainsTvAction() {
    myUrl = ApplicationInfo.getInstance().getJetbrainsTvUrl();
  }

  @Override
  public void update(AnActionEvent e) {
    boolean enabled = myUrl != null;
    e.getPresentation().setVisible(enabled);
    e.getPresentation().setEnabled(enabled);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    if (myUrl != null) {
      BrowserUtil.browse(myUrl);
    }
  }
}