// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.HelpID;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaMethodBreakpointProperties;

import javax.swing.*;

/**
 * @author Egor
 */
public final class JavaWildcardMethodBreakpointType extends JavaBreakpointTypeBase<JavaMethodBreakpointProperties> {
  public JavaWildcardMethodBreakpointType() {
    super("java-wildcard-method", JavaDebuggerBundle.message("method.breakpoints.tab.title"));
  }

  @Override
  public @NotNull Icon getEnabledIcon() {
    return AllIcons.Debugger.Db_method_breakpoint;
  }

  @Override
  public @NotNull Icon getDisabledIcon() {
    return AllIcons.Debugger.Db_disabled_method_breakpoint;
  }

  @Override
  public @NotNull Icon getMutedEnabledIcon() {
    return AllIcons.Debugger.Db_muted_method_breakpoint;
  }

  @Override
  public @NotNull Icon getMutedDisabledIcon() {
    return AllIcons.Debugger.Db_muted_disabled_method_breakpoint;
  }

  //@Override
  private static String getHelpID() {
    return HelpID.METHOD_BREAKPOINTS;
  }

  //@Override
  public String getDisplayName() {
    return JavaDebuggerBundle.message("method.breakpoints.tab.title");
  }

  @Override
  public @Nls String getGeneralDescription(XBreakpoint<JavaMethodBreakpointProperties> breakpoint) {
    return JavaDebuggerBundle.message("method.breakpoint.description");
  }

  @Override
  public String getDisplayText(XBreakpoint<JavaMethodBreakpointProperties> breakpoint) {
    return JavaMethodBreakpointType.getText(breakpoint);
  }

  @Override
  public @Nullable XBreakpointCustomPropertiesPanel<XBreakpoint<JavaMethodBreakpointProperties>> createCustomPropertiesPanel(@NotNull Project project) {
    return new MethodBreakpointPropertiesPanel();
  }

  //@Override
  //public Key<MethodBreakpoint> getBreakpointCategory() {
  //  return MethodBreakpoint.CATEGORY;
  //}

  @Override
  public @Nullable JavaMethodBreakpointProperties createProperties() {
    return new JavaMethodBreakpointProperties();
  }

  @Override
  public @Nullable XBreakpoint<JavaMethodBreakpointProperties> addBreakpoint(final Project project, JComponent parentComponent) {
    final AddWildcardBreakpointDialog dialog = new AddWildcardBreakpointDialog(project);
    if (!dialog.showAndGet()) {
      return null;
    }
    JavaMethodBreakpointProperties properties = new JavaMethodBreakpointProperties(dialog.getClassPattern(), dialog.getMethodName());
    if (Registry.is("debugger.emulate.method.breakpoints")) {
      properties.EMULATED = true; // create all new emulated
    }
    if (Registry.is("debugger.method.breakpoints.entry.default")) {
      properties.WATCH_EXIT = false;
    }
    return XDebuggerManager.getInstance(project).getBreakpointManager().addBreakpoint(this, properties);
  }

  @Override
  public @NotNull Breakpoint<JavaMethodBreakpointProperties> createJavaBreakpoint(Project project, XBreakpoint<JavaMethodBreakpointProperties> breakpoint) {
    return new WildcardMethodBreakpoint(project, breakpoint);
  }
}
