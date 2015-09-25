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

import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.RegistryToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import org.jetbrains.annotations.NotNull;

/**
 * @author egor
 */
public class ShowTypesAction extends RegistryToggleAction {
  public ShowTypesAction() {
    super("debugger.showTypes");
  }

  @Override
  public void doWhenDone(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      if (e.getData(XDebugSessionTab.TAB_KEY) == null) {
        XDebuggerTree tree = XDebuggerTree.getTree(e);
        if (tree != null) {
          tree.rebuildAndRestore(XDebuggerTreeState.saveState(tree));
        }
      }
      XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
      if (session != null) {
        session.rebuildViews();
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
      if (session != null && session.getDebugProcess() instanceof JavaDebugProcess) {
        e.getPresentation().setEnabledAndVisible(true);
        super.update(e);
        return;
      }
    }
    e.getPresentation().setEnabledAndVisible(false);
  }
}
