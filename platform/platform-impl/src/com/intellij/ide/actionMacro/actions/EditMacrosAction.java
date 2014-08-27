/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.actionMacro.actions;

import com.intellij.ide.actionMacro.ActionMacro;
import com.intellij.ide.actionMacro.ActionMacroConfigurable;
import com.intellij.ide.actionMacro.ActionMacroManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;

public class EditMacrosAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(AnActionEvent e) {
    ShowSettingsUtil.getInstance().editConfigurable(e.getProject(), "#com.intellij.ide.actionMacro.EditMacrosDialog", new ActionMacroConfigurable());
  }

  @Override
  public void update(AnActionEvent e) {
    ActionMacro[] macros = ActionMacroManager.getInstance().getAllMacros();
    e.getPresentation().setEnabled(macros != null && macros.length > 0);
  }
}
