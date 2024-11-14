// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.ArrayUtil;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;

public class AddSteppingFilterAction extends DebuggerAction {
  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final DebuggerContextImpl debuggerContext = getDebuggerContext(e.getDataContext());
    DebugProcessImpl process = debuggerContext.getDebugProcess();
    if (process == null) {
      return;
    }
    StackFrameProxyImpl proxy = getStackFrameProxy(e);
    DebuggerManagerThreadImpl managerThread = debuggerContext.getManagerThread();
    if (managerThread == null) return;
    managerThread.schedule(new DebuggerCommandImpl() {
      @Override
      protected void action() {
        final String name = getClassName(proxy != null ? proxy : debuggerContext.getFrameProxy());
        if (name == null) {
          return;
        }

        final Project project = e.getData(CommonDataKeys.PROJECT);
        ApplicationManager.getApplication().invokeLater(() -> {
          String filter = Messages.showInputDialog(project, "", JavaDebuggerBundle.message("add.stepping.filter"), null, name, null);
          if (filter != null) {
            ClassFilter[] newFilters = ArrayUtil.append(DebuggerSettings.getInstance().getSteppingFilters(), new ClassFilter(filter));
            DebuggerSettings.getInstance().setSteppingFilters(newFilters);
          }
        });
      }
    });
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(getStackFrameProxy(e) != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
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
