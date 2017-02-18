/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.debugger.memory.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import com.intellij.debugger.memory.ui.InstancesWindow;

public class ShowInstancesByClassAction extends DebuggerTreeAction {
  @Override
  protected boolean isEnabled(@NotNull XValueNodeImpl node, @NotNull AnActionEvent e) {
    ObjectReference ref = getObjectReference(node);
    if(ref != null) {
      String text = String.format("Show %s Objects...", StringUtil.getShortName(ref.referenceType().name()));
      e.getPresentation().setText(text);
    }

    return ref != null;
  }

  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      XDebugSession debugSession = XDebuggerManager.getInstance(project).getCurrentSession();
      ObjectReference ref = getObjectReference(node);
      if(debugSession != null && ref != null){
        ReferenceType referenceType = ref.referenceType();
        new InstancesWindow(debugSession, referenceType::instances, referenceType.name()).show();
      }
    }
  }
}
