// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.HelpID;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaExceptionBreakpointProperties;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 */
public final class JavaExceptionBreakpointType extends JavaBreakpointTypeBase<JavaExceptionBreakpointProperties> {
  public JavaExceptionBreakpointType() {
    super("java-exception", JavaDebuggerBundle.message("exception.breakpoints.tab.title"));
  }

  @Override
  public @NotNull Icon getEnabledIcon() {
    return AllIcons.Debugger.Db_exception_breakpoint;
  }

  @Override
  public @NotNull Icon getDisabledIcon() {
    return AllIcons.Debugger.Db_disabled_exception_breakpoint;
  }

  @Override
  public @NotNull Icon getMutedEnabledIcon() {
    return AllIcons.Debugger.Db_exception_breakpoint;
  }

  @Override
  public @NotNull Icon getMutedDisabledIcon() {
    return AllIcons.Debugger.Db_exception_breakpoint;
  }

  //@Override
  private static String getHelpID() {
    return HelpID.EXCEPTION_BREAKPOINTS;
  }

  //@Override
  public String getDisplayName() {
    return JavaDebuggerBundle.message("exception.breakpoints.tab.title");
  }

  @Override
  public @Nls String getGeneralDescription(XBreakpoint<JavaExceptionBreakpointProperties> breakpoint) {
    String name = breakpoint.getProperties().myQualifiedName;
    if (name != null) {
      return JavaDebuggerBundle.message("exception.breakpoint.description.with.type", name);
    }
    else {
      return JavaDebuggerBundle.message("exception.breakpoint.description.any");
    }
  }

  @Override
  public String getDisplayText(XBreakpoint<JavaExceptionBreakpointProperties> breakpoint) {
    String name = breakpoint.getProperties().myQualifiedName;
    if (name != null) {
      return name;
    }
    else {
      return JavaDebuggerBundle.message("breakpoint.any.exception.display.name");
    }
  }

  @Override
  public @NotNull JavaExceptionBreakpointProperties createProperties() {
    return new JavaExceptionBreakpointProperties();
  }

  @Override
  public @NotNull XBreakpointCustomPropertiesPanel<XBreakpoint<JavaExceptionBreakpointProperties>> createCustomPropertiesPanel(@NotNull Project project) {
    return new ExceptionBreakpointPropertiesPanel();
  }

  @Override
  public @NotNull XBreakpoint<JavaExceptionBreakpointProperties> createDefaultBreakpoint(@NotNull XBreakpointCreator<JavaExceptionBreakpointProperties> creator) {
    return creator.createBreakpoint(new JavaExceptionBreakpointProperties());
  }

  //public Key<ExceptionBreakpoint> getBreakpointCategory() {
  //  return ExceptionBreakpoint.CATEGORY;
  //}

  @Override
  public @Nullable XBreakpoint<JavaExceptionBreakpointProperties> addBreakpoint(final Project project, JComponent parentComponent) {
    final PsiClass throwableClass =
      JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_THROWABLE, GlobalSearchScope.allScope(project));
    TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project)
      .createInheritanceClassChooser(JavaDebuggerBundle.message("add.exception.breakpoint.classchooser.title"),
                                     GlobalSearchScope.allScope(project), throwableClass, true, true, null);
    chooser.showDialog();
    final PsiClass selectedClass = chooser.getSelected();
    final String qName = selectedClass == null ? null : JVMNameUtil.getClassVMName(selectedClass);

    if (qName != null && !qName.isEmpty()) {
      return XDebuggerManager.getInstance(project).getBreakpointManager()
        .addBreakpoint(this, new JavaExceptionBreakpointProperties(qName));
    }
    return null;
  }

  @Override
  public @NotNull Breakpoint<JavaExceptionBreakpointProperties> createJavaBreakpoint(Project project, XBreakpoint<JavaExceptionBreakpointProperties> breakpoint) {
    if (!XDebuggerManager.getInstance(project).getBreakpointManager().isDefaultBreakpoint(breakpoint)) {
      return new ExceptionBreakpoint(project, breakpoint);
    }
    else {
      return new AnyExceptionBreakpoint(project, breakpoint);
    }
  }
}
