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
package com.intellij.ide.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.project.DumbAware;

/**
 * @author max
 */
public class WhatsNewAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    BrowserUtil.browse(ApplicationInfoEx.getInstanceEx().getWhatsNewUrl());
  }

  @Override
  public void update(AnActionEvent e) {
    boolean visible = ApplicationInfoEx.getInstanceEx().getWhatsNewUrl() != null;
    e.getPresentation().setVisible(visible);
    if (visible) {
      e.getPresentation()
        .setText(IdeBundle.message("whatsnew.action.custom.text", ApplicationNamesInfo.getInstance().getFullProductName()));
      e.getPresentation().setDescription(
        IdeBundle.message("whatsnew.action.custom.description", ApplicationNamesInfo.getInstance().getFullProductName()));
    }
  }
}
