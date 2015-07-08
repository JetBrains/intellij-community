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
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.HotSwapUI;
import com.intellij.debugger.ui.HotSwapUIImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;

/**
 * @author lex
 */
public class HotSwapAction extends AnAction{

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if(project == null) {
      return;
    }

    DebuggerManagerEx debuggerManager = DebuggerManagerEx.getInstanceEx(project);
    DebuggerSession session = debuggerManager.getContext().getDebuggerSession();

    if(session != null && session.isAttached()) {
      HotSwapUI.getInstance(project).reloadChangedClasses(session, DebuggerSettings.getInstance().COMPILE_BEFORE_HOTSWAP);
    }
  }

  public void update(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if(project == null) {
      e.getPresentation().setEnabled(false);
      return;
    }

    DebuggerManagerEx debuggerManager = DebuggerManagerEx.getInstanceEx(project);
    DebuggerSession session = debuggerManager.getContext().getDebuggerSession();

    e.getPresentation().setEnabled(session != null && HotSwapUIImpl.canHotSwap(session));
  }
}
