/*
 * Class EvaluateAction
 * @author Jeka
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.ValueHint;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.evaluate.quick.common.QuickEvaluateHandler;
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class QuickEvaluateActionHandler extends QuickEvaluateHandler {

  public boolean isEnabled(@NotNull final Project project) {
    DebuggerSession debuggerSession = DebuggerManagerEx.getInstanceEx(project).getContext().getDebuggerSession();
    return debuggerSession != null && debuggerSession.isPaused();
  }

  public AbstractValueHint createValueHint(@NotNull final Project project, @NotNull final Editor editor, @NotNull final Point point, final int type) {
    return ValueHint.createValueHint(project, editor, point, type);
  }

  public boolean canShowHint(@NotNull final Project project) {
    DebuggerSession debuggerSession = DebuggerManagerEx.getInstanceEx(project).getContext().getDebuggerSession();
    return debuggerSession != null && debuggerSession.isAttached();
  }

  public int getValueLookupDelay() {
    return DebuggerSettings.getInstance().VALUE_LOOKUP_DELAY;
  }
}
