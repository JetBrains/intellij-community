// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.HelpID;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
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
public class JavaWildcardMethodBreakpointType extends JavaBreakpointTypeBase<JavaMethodBreakpointProperties> {
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
  public XBreakpointCustomPropertiesPanel<XBreakpoint<JavaMethodBreakpointProperties>> createCustomPropertiesPanel(@NotNull Project project) {
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
    return WriteAction.compute(() -> {
      JavaMethodBreakpointProperties properties = new JavaMethodBreakpointProperties(dialog.getClassPattern(), dialog.getMethodName());
      if (Registry.is("debugger.emulate.method.breakpoints")) {
        properties.EMULATED = true; // create all new emulated
      }
      return XDebuggerManager.getInstance(project).getBreakpointManager().addBreakpoint(this, properties);
    });
  }

  @NotNull
  @Override
  public Breakpoint<JavaMethodBreakpointProperties> createJavaBreakpoint(Project project, XBreakpoint<JavaMethodBreakpointProperties> breakpoint) {
    return new WildcardMethodBreakpoint(project, breakpoint);
  }
}
