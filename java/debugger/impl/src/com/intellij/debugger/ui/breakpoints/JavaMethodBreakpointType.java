// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.HelpID;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiMethod;
import com.intellij.util.SmartList;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xml.CommonXmlStrings;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaMethodBreakpointProperties;

import javax.swing.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class JavaMethodBreakpointType extends JavaLineBreakpointTypeBase<JavaMethodBreakpointProperties> {
  public JavaMethodBreakpointType(@NotNull String id, @Nls @NotNull String message) {
    super(id, message);
  }

  public JavaMethodBreakpointType() {
    this("java-method", JavaDebuggerBundle.message("method.breakpoints.tab.title"));
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
  public Icon getSuspendNoneIcon() {
    return AllIcons.Debugger.Db_no_suspend_method_breakpoint;
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

  @NotNull
  @Override
  public Icon getInactiveDependentIcon() {
    return AllIcons.Debugger.Db_dep_method_breakpoint;
  }

  //@Override
  protected String getHelpID() {
    return HelpID.METHOD_BREAKPOINTS;
  }

  //@Override
  public String getDisplayName() {
    return JavaDebuggerBundle.message("method.breakpoints.tab.title");
  }

  @Nls
  @Override
  protected @NotNull String getGeneralDescription(XLineBreakpointType<JavaMethodBreakpointProperties>.XLineBreakpointVariant variant) {
    return JavaDebuggerBundle.message("method.breakpoint.description");
  }

  @Nls
  @Override
  public String getGeneralDescription(XLineBreakpoint<JavaMethodBreakpointProperties> breakpoint) {
    return JavaDebuggerBundle.message("method.breakpoint.description");
  }

  @Override
  public List<@Nls String> getPropertyXMLDescriptions(XLineBreakpoint<JavaMethodBreakpointProperties> breakpoint) {
    var res = new SmartList<>(super.getPropertyXMLDescriptions(breakpoint));
    var props = breakpoint.getProperties();
    if (props != null) {
      var defaults = createProperties();
      // Add only non-default values of properties.
      if (props.EMULATED != defaults.EMULATED) {
        res.add(JavaDebuggerBundle.message("method.breakpoint.property.name.emulated") + CommonXmlStrings.NBSP
                + props.EMULATED);
      }
      if (props.WATCH_ENTRY != defaults.WATCH_ENTRY || props.WATCH_EXIT != defaults.WATCH_EXIT) {
        // Add both if at least one property isn't default.
        res.add(JavaDebuggerBundle.message("method.breakpoint.property.name.watch.entry") + CommonXmlStrings.NBSP
                + props.WATCH_ENTRY);
        res.add(JavaDebuggerBundle.message("method.breakpoint.property.name.watch.exit") + CommonXmlStrings.NBSP
                + props.WATCH_EXIT);
      }
    }
    return res;
  }

  @Override
  public String getShortText(XLineBreakpoint<JavaMethodBreakpointProperties> breakpoint) {
    return getText(breakpoint);
  }

  @Nls
  static String getText(XBreakpoint<JavaMethodBreakpointProperties> breakpoint) {
    final StringBuilder buffer = new StringBuilder();
    //if (isValid()) {
    final String className = breakpoint.getProperties().myClassPattern;
    final boolean classNameExists = className != null && !className.isEmpty();
    if (classNameExists) {
      buffer.append(className);
    }
    if (breakpoint.getProperties().myMethodName != null) {
      if (classNameExists) {
        buffer.append(".");
      }
      buffer.append(breakpoint.getProperties().myMethodName);
    }
    //}
    //else {
    //  buffer.append(JavaDebuggerBundle.message("status.breakpoint.invalid"));
    //}
    //noinspection HardCodedStringLiteral
    return buffer.toString();
  }

  @Nullable
  @Override
  public XBreakpointCustomPropertiesPanel createCustomPropertiesPanel(@NotNull Project project) {
    return new MethodBreakpointPropertiesPanel();
  }

  @Nullable
  @Override
  public JavaMethodBreakpointProperties createProperties() {
    JavaMethodBreakpointProperties properties = new JavaMethodBreakpointProperties();
    if (Registry.is("debugger.emulate.method.breakpoints")) {
      properties.EMULATED = true; // create all new emulated
    }
    if (Registry.is("debugger.method.breakpoints.entry.default")) {
      properties.WATCH_EXIT = false;
    }
    return properties;
  }

  @Nullable
  @Override
  public JavaMethodBreakpointProperties createBreakpointProperties(@NotNull VirtualFile file, int line) {
    return createProperties();
  }

  @NotNull
  @Override
  public Breakpoint<JavaMethodBreakpointProperties> createJavaBreakpoint(Project project, XBreakpoint<JavaMethodBreakpointProperties> breakpoint) {
    return new MethodBreakpoint(project, breakpoint);
  }

  @Override
  public boolean canBeHitInOtherPlaces() {
    return true;
  }

  @Override
  public boolean canPutAt(@NotNull VirtualFile file, int line, @NotNull Project project) {
    return canPutAtElement(file, line, project, (element, document) -> element instanceof PsiMethod);
  }
}
