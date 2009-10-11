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
package com.intellij.debugger.actions;

import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.DebuggerPanelsManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;

/**
 * User: lex
 * Date: Sep 26, 2003
 * Time: 7:45:21 PM
 */
public class ShowFrameAction extends GotoFrameSourceAction {
  public void actionPerformed(AnActionEvent e) {
    super.actionPerformed(e);
    DebuggerStateManager stateManager = getContextManager(e.getDataContext());
    DebuggerContextImpl context = stateManager.getContext();

    if(context != null) {
      DebuggerPanelsManager.getInstance(context.getProject()).showFramePanel();
    }
  }
}
