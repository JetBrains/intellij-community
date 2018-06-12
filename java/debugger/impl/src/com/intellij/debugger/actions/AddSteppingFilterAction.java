// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
      protected void action() {
        final String name = getClassName(proxy != null ? proxy : debuggerContext.getFrameProxy());
        if (name == null) {
          return;
        }

        final Project project = e.getData(CommonDataKeys.PROJECT);
        ApplicationManager.getApplication().invokeLater(() -> {
          String filter = Messages.showInputDialog(project, "", "Add Stepping Filter", null, name, null);
          if (filter != null) {
            ClassFilter[] newFilters = ArrayUtil.append(DebuggerSettings.getInstance().getSteppingFilters(), new ClassFilter(filter));
            DebuggerSettings.getInstance().setSteppingFilters(newFilters);
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
