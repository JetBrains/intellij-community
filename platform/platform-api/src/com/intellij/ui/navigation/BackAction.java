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
package com.intellij.ui.navigation;

import com.intellij.openapi.actionSystem.AnActionEvent;

import javax.swing.*;

public class BackAction extends NavigationAction {

  public BackAction(JComponent c) {
    super(c, "Back");
  }

  @Override
  protected void doUpdate(final AnActionEvent e) {
    e.getPresentation().setEnabled(getHistory(e).canGoBack());
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    getHistory(e).back();
  }
}
