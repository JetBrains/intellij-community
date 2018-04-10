// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.actions;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.SuspendManagerImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author egor
 */
public class SwitchToTheNextContextAction extends DebuggerAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());
    DebugProcessImpl process = debuggerContext.getDebugProcess();
    if (process == null) {
      return;
    }
    process.getManagerThread().schedule(new DebuggerContextCommandImpl(debuggerContext) {
      @Override
      public void threadAction(@NotNull SuspendContextImpl suspendContext) {
        List<SuspendContextImpl> pausedContexts = ((SuspendManagerImpl)process.getSuspendManager()).getPausedContexts();
        if (pausedContexts.size() > 1) {
          int currentIndex = pausedContexts.indexOf(debuggerContext.getSuspendContext());
          int newIndex = (currentIndex + 1) % pausedContexts.size();
          process.getManagerThread().schedule(new SuspendContextCommandImpl(pausedContexts.get(newIndex)) {
            @Override
            public void contextAction(@NotNull SuspendContextImpl suspendContext) {
              process.getSession().switchContext(suspendContext);
            }
          });
        }
      }
    });
  }
}
