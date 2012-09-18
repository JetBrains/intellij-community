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

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.ShadowAction;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

abstract class NavigationAction extends AnAction implements DumbAware {

  private final ShadowAction myShadow;

  protected NavigationAction(JComponent c, final String originalActionID) {
    final AnAction original = ActionManager.getInstance().getAction(originalActionID);
    myShadow = new ShadowAction(this, original, c);
    getTemplatePresentation().setIcon(original.getTemplatePresentation().getIcon());
  }

  @Override
  public final void update(final AnActionEvent e) {
    e.getPresentation().setEnabled(getHistory(e) != null);
    if (e.getPresentation().isEnabled()) {
      doUpdate(e);
    }
  }

  protected abstract void doUpdate(final AnActionEvent e);

  @Nullable
  protected static History getHistory(final AnActionEvent e) {
    return e.getData(History.KEY);
  }

}
