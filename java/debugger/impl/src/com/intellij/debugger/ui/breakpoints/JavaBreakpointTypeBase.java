/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.debugger.ui.breakpoints;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
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

/**
 * Base class for non-line java breakpoint
 * @author egor
 */
public abstract class JavaBreakpointTypeBase<T extends JavaBreakpointProperties> extends XBreakpointType<XBreakpoint<T>, T> {
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
}
