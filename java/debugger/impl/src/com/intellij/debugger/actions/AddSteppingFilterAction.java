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

/*
 * @author egor
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.ArrayUtil;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;

public class AddSteppingFilterAction extends DebuggerAction {
  public void actionPerformed(final AnActionEvent e) {
    final DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());
    DebugProcessImpl process = debuggerContext.getDebugProcess();
    if (process == null) {
      return;
    }
    final StackFrameProxyImpl proxy = PopFrameAction.getStackFrameProxy(e);
    process.getManagerThread().schedule(new DebuggerCommandImpl() {
      protected void action() throws Exception {
        final String name = getClassName(proxy != null ? proxy : debuggerContext.getFrameProxy());
        if (name == null) {
          return;
        }

        final Project project = e.getData(CommonDataKeys.PROJECT);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            String filter = Messages.showInputDialog(project, "", "Add Stepping Filter", null, name, null);
            if (filter != null) {
              ClassFilter[] newFilters = ArrayUtil.append(DebuggerSettings.getInstance().getSteppingFilters(), new ClassFilter(filter));
              DebuggerSettings.getInstance().setSteppingFilters(newFilters);
            }
          }
        });
      }
    });
  }

  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(PopFrameAction.getStackFrameProxy(e) != null);
  }

  private static String getClassName(StackFrameProxyImpl stackFrameProxy) {
    if (stackFrameProxy != null) {
      try {
        Location location = stackFrameProxy.location();
        if (location != null) {
          ReferenceType type = location.declaringType();
          if (type != null) {
            return type.name();
          }
        }
      }
      catch (EvaluateException ignore) {
      }
    }
    return null;
  }
}
