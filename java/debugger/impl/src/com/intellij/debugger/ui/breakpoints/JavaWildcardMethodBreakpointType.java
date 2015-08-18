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
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.HelpID;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaMethodBreakpointProperties;

import javax.swing.*;

/**
 * @author Egor
 */
public class JavaWildcardMethodBreakpointType extends JavaBreakpointTypeBase<JavaMethodBreakpointProperties>
                                              implements JavaBreakpointType<JavaMethodBreakpointProperties> {

  public JavaWildcardMethodBreakpointType() {
    super("java-wildcard-method", DebuggerBundle.message("method.breakpoints.tab.title"));
  }

  @NotNull
  @Override
  public Icon getEnabledIcon() {
    return AllIcons.Debugger.Db_method_breakpoint;
  }

  @NotNull
  @Override
  public Icon getDisabledIcon() {
    return AllIcons.Debugger.Db_disabled_method_breakpoint;
  }

  @NotNull
  @Override
  public Icon getMutedEnabledIcon() {
    return AllIcons.Debugger.Db_muted_method_breakpoint;
  }

  @NotNull
  @Override
  public Icon getMutedDisabledIcon() {
    return AllIcons.Debugger.Db_muted_disabled_method_breakpoint;
  }

  //@Override
  protected String getHelpID() {
    return HelpID.METHOD_BREAKPOINTS;
  }

  //@Override
  public String getDisplayName() {
    return DebuggerBundle.message("method.breakpoints.tab.title");
  }

  @Override
  public String getDisplayText(XBreakpoint<JavaMethodBreakpointProperties> breakpoint) {
    return JavaMethodBreakpointType.getText(breakpoint);
  }

  @Nullable
  @Override
  public XBreakpointCustomPropertiesPanel<XBreakpoint<JavaMethodBreakpointProperties>> createCustomPropertiesPanel() {
    return new MethodBreakpointPropertiesPanel();
  }

  //@Override
  //public Key<MethodBreakpoint> getBreakpointCategory() {
  //  return MethodBreakpoint.CATEGORY;
  //}

  @Nullable
  @Override
  public JavaMethodBreakpointProperties createProperties() {
    return new JavaMethodBreakpointProperties();
  }

  @Nullable
  @Override
  public XBreakpoint<JavaMethodBreakpointProperties> addBreakpoint(final Project project, JComponent parentComponent) {
    final AddWildcardBreakpointDialog dialog = new AddWildcardBreakpointDialog(project);
    if (!dialog.showAndGet()) {
      return null;
    }
    return ApplicationManager.getApplication().runWriteAction(new Computable<XBreakpoint<JavaMethodBreakpointProperties>>() {
      @Override
      public XBreakpoint<JavaMethodBreakpointProperties> compute() {
        return XDebuggerManager.getInstance(project).getBreakpointManager()
          .addBreakpoint(JavaWildcardMethodBreakpointType.this, new JavaMethodBreakpointProperties(
            dialog.getClassPattern(),
            dialog.getMethodName()));
      }
    });
  }

  @NotNull
  @Override
  public Breakpoint<JavaMethodBreakpointProperties> createJavaBreakpoint(Project project, XBreakpoint<JavaMethodBreakpointProperties> breakpoint) {
    return new WildcardMethodBreakpoint(project, breakpoint);
  }
}
