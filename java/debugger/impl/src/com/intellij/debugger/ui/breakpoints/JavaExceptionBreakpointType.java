// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.HelpID;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaExceptionBreakpointProperties;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 */
public class JavaExceptionBreakpointType extends JavaBreakpointTypeBase<JavaExceptionBreakpointProperties> {
  public JavaExceptionBreakpointType() {
    super("java-exception", JavaDebuggerBundle.message("exception.breakpoints.tab.title"));
  }

  @NotNull
  @Override
  public Icon getEnabledIcon() {
    return AllIcons.Debugger.Db_exception_breakpoint;
  }

  @NotNull
  @Override
  public Icon getDisabledIcon() {
    return AllIcons.Debugger.Db_disabled_exception_breakpoint;
  }

  @NotNull
  @Override
  public Icon getMutedEnabledIcon() {
    return AllIcons.Debugger.Db_exception_breakpoint;
  }

  @NotNull
  @Override
  public Icon getMutedDisabledIcon() {
    return AllIcons.Debugger.Db_exception_breakpoint;
  }

  //@Override
  protected String getHelpID() {
    return HelpID.EXCEPTION_BREAKPOINTS;
  }

  //@Override
  public String getDisplayName() {
    return JavaDebuggerBundle.message("exception.breakpoints.tab.title");
  }

  @Override
  public String getDisplayText(XBreakpoint<JavaExceptionBreakpointProperties> breakpoint) {
    String name = breakpoint.getProperties().myQualifiedName;
    if (name != null) {
      return JavaDebuggerBundle.message("breakpoint.exception.breakpoint.display.name", name);
    }
    else {
      return JavaDebuggerBundle.message("breakpoint.any.exception.display.name");
    }
  }

  @Nullable
  @Override
  public JavaExceptionBreakpointProperties createProperties() {
    return new JavaExceptionBreakpointProperties();
  }

  @Nullable
  @Override
  public XBreakpointCustomPropertiesPanel<XBreakpoint<JavaExceptionBreakpointProperties>> createCustomPropertiesPanel(@NotNull Project project) {
    return new ExceptionBreakpointPropertiesPanel();
  }

  @Nullable
  @Override
  public XBreakpoint<JavaExceptionBreakpointProperties> createDefaultBreakpoint(@NotNull XBreakpointCreator<JavaExceptionBreakpointProperties> creator) {
    return creator.createBreakpoint(new JavaExceptionBreakpointProperties());
  }

  //public Key<ExceptionBreakpoint> getBreakpointCategory() {
  //  return ExceptionBreakpoint.CATEGORY;
  //}

  @Nullable
  @Override
  public XBreakpoint<JavaExceptionBreakpointProperties> addBreakpoint(final Project project, JComponent parentComponent) {
    final PsiClass throwableClass =
      JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_THROWABLE, GlobalSearchScope.allScope(project));
    TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project)
      .createInheritanceClassChooser(JavaDebuggerBundle.message("add.exception.breakpoint.classchooser.title"),
                                     GlobalSearchScope.allScope(project), throwableClass, true, true, null);
    chooser.showDialog();
    final PsiClass selectedClass = chooser.getSelected();
    final String qName = selectedClass == null ? null : JVMNameUtil.getNonAnonymousClassName(selectedClass);

    if (qName != null && qName.length() > 0) {
      return WriteAction.compute(() -> XDebuggerManager.getInstance(project).getBreakpointManager()
        .addBreakpoint(this, new JavaExceptionBreakpointProperties(qName, ((PsiClassOwner)selectedClass.getContainingFile()).getPackageName())));
    }
    return null;
  }

  @NotNull
  @Override
  public Breakpoint<JavaExceptionBreakpointProperties> createJavaBreakpoint(Project project, XBreakpoint<JavaExceptionBreakpointProperties> breakpoint) {
    if (!XDebuggerManager.getInstance(project).getBreakpointManager().isDefaultBreakpoint(breakpoint)) {
      return new ExceptionBreakpoint(project, breakpoint);
    }
    else {
      return new AnyExceptionBreakpoint(project, breakpoint);
    }
  }
}
