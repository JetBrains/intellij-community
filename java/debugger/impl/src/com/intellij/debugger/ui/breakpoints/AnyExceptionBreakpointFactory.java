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
package com.intellij.debugger.ui.breakpoints;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Key;
import com.intellij.xdebugger.ui.DebuggerIcons;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 26, 2005
 */
public class AnyExceptionBreakpointFactory extends BreakpointFactory{
  public Breakpoint createBreakpoint(Project project, final Element element) {
    return new AnyExceptionBreakpoint(project);
  }

  public Icon getIcon() {
    return DebuggerIcons.ENABLED_EXCEPTION_BREAKPOINT_ICON;
  }

  public Icon getDisabledIcon() {
    return DebuggerIcons.DISABLED_EXCEPTION_BREAKPOINT_ICON;
  }

  public @Nullable BreakpointPanel createBreakpointPanel(Project project, DialogWrapper parentDialog) {
    return null;
  }

  public Key<AnyExceptionBreakpoint> getBreakpointCategory() {
    return AnyExceptionBreakpoint.ANY_EXCEPTION_BREAKPOINT;
  }
}
