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
package com.intellij.ide.actionMacro.actions;

import com.intellij.ide.actionMacro.ActionMacro;
import com.intellij.ide.actionMacro.ActionMacroManager;
import com.intellij.ide.actionMacro.EditMacrosDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jul 22, 2003
 * Time: 3:33:04 PM
 * To change this template use Options | File Templates.
 */
public class EditMacrosAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    EditMacrosDialog dialog = new EditMacrosDialog(CommonDataKeys.PROJECT.getData(e.getDataContext()));
    dialog.show();
  }

  public void update(AnActionEvent e) {
    final ActionMacroManager manager = ActionMacroManager.getInstance();
    ActionMacro[] macros = manager.getAllMacros();
    e.getPresentation().setEnabled(macros != null && macros.length > 0);
  }
}
