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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jdom.Element;

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
    return AllIcons.Debugger.Db_exception_breakpoint;
  }

  public Icon getDisabledIcon() {
    return AllIcons.Debugger.Db_disabled_exception_breakpoint;
  }

  @Override
  protected String getHelpID() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public String getDisplayName() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public BreakpointPropertiesPanel createBreakpointPropertiesPanel(Project project, boolean compact) {
    return new ExceptionBreakpointPropertiesPanel(project, compact);
  }

  @Override
  public boolean breakpointCanBeRemoved(Breakpoint breakpoint) {
    return false;
  }

  public Key<AnyExceptionBreakpoint> getBreakpointCategory() {
    return AnyExceptionBreakpoint.ANY_EXCEPTION_BREAKPOINT;
  }
}
