// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.JavaDebuggerEditorsProvider;
import org.jetbrains.java.debugger.breakpoints.JavaBreakpointFiltersPanel;
import org.jetbrains.java.debugger.breakpoints.properties.JavaBreakpointProperties;

import java.util.List;

/**
 * Base class for non-line java breakpoint
 * @author egor
 */
public abstract class JavaBreakpointTypeBase<T extends JavaBreakpointProperties> extends XBreakpointType<XBreakpoint<T>, T>
  implements JavaBreakpointType<T> {
  protected JavaBreakpointTypeBase(@NonNls @NotNull String id, @Nls @NotNull String title) {
    super(id, title, true);
  }

  @Override
  public final boolean isAddBreakpointButtonVisible() {
    return true;
  }

  @NotNull
  @Override
  public final XBreakpointCustomPropertiesPanel<XBreakpoint<T>> createCustomRightPropertiesPanel(@NotNull Project project) {
    return new JavaBreakpointFiltersPanel<>(project);
  }

  @NotNull
  @Override
  public final XDebuggerEditorsProvider getEditorsProvider(@NotNull XBreakpoint<T> breakpoint, @NotNull Project project) {
    return new JavaDebuggerEditorsProvider();
  }

  @Nullable
  @Override
  public XSourcePosition getSourcePosition(@NotNull XBreakpoint<T> breakpoint) {
    Breakpoint javaBreakpoint = BreakpointManager.getJavaBreakpoint(breakpoint);
    if (javaBreakpoint != null) {
      PsiClass aClass = javaBreakpoint.getPsiClass();
      if (aClass != null) {
        return ReadAction.compute(() -> XDebuggerUtil.getInstance().createPositionByElement(aClass));
      }
    }
    return null;
  }

  @Override
  public List<? extends AnAction> getAdditionalPopupMenuActions(@NotNull XBreakpoint<T> breakpoint,
                                                                @Nullable XDebugSession currentSession) {
    return BreakpointIntentionAction.getIntentions(breakpoint, currentSession);
  }
}
