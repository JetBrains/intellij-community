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
package com.intellij.ide;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.impl.welcomeScreen.NewRecentProjectPanel;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class ManageRecentProjectsAction extends DumbAwareAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Disposable disposable = Disposer.newDisposable();
    NewRecentProjectPanel panel = new NewRecentProjectPanel(disposable);
    JList list = UIUtil.findComponentOfType(panel, JList.class);
    JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, list)
      .setTitle("Recent Projects")
      .setFocusable(true)
      .setMovable(true)
      .createPopup();
    Disposer.register(popup, disposable);
    popup.showCenteredInCurrentWindow(e.getProject());
  }
}
