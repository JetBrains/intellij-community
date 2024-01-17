// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.CommonBundle;
import com.intellij.debugger.HelpID;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaFieldBreakpointProperties;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 */
public final class JavaFieldBreakpointType extends JavaLineBreakpointTypeBase<JavaFieldBreakpointProperties> {
  public JavaFieldBreakpointType() {
    super("java-field", JavaDebuggerBundle.message("field.watchpoints.tab.title"));
  }

  @Override
  public boolean isAddBreakpointButtonVisible() {
    return true;
  }

  @NotNull
  @Override
  public Icon getEnabledIcon() {
    return AllIcons.Debugger.Db_field_breakpoint;
  }

  @NotNull
  @Override
  public Icon getDisabledIcon() {
    return AllIcons.Debugger.Db_disabled_field_breakpoint;
  }

  @NotNull
  @Override
  public Icon getSuspendNoneIcon() {
    return AllIcons.Debugger.Db_no_suspend_field_breakpoint;
  }

  @NotNull
  @Override
  public Icon getMutedEnabledIcon() {
    return AllIcons.Debugger.Db_muted_field_breakpoint;
  }

  @NotNull
  @Override
  public Icon getMutedDisabledIcon() {
    return AllIcons.Debugger.Db_muted_disabled_field_breakpoint;
  }

  @NotNull
  @Override
  public Icon getInactiveDependentIcon() {
    return AllIcons.Debugger.Db_dep_field_breakpoint;
  }

  //@Override
  private static String getHelpID() {
    return HelpID.FIELD_WATCHPOINTS;
  }

  //@Override
  public String getDisplayName() {
    return JavaDebuggerBundle.message("field.watchpoints.tab.title");
  }

  @Nls
  @Override
  protected @NotNull String getGeneralDescription(XLineBreakpointType<JavaFieldBreakpointProperties>.XLineBreakpointVariant variant) {
    return JavaDebuggerBundle.message("field.watchpoint.description");
  }

  @Nls
  @Override
  public String getGeneralDescription(XLineBreakpoint<JavaFieldBreakpointProperties> breakpoint) {
    return JavaDebuggerBundle.message("field.watchpoint.description");
  }

  @Override
  public String getShortText(XLineBreakpoint<JavaFieldBreakpointProperties> breakpoint) {
    return getText(breakpoint, true);
  }

  @Nls
  public String getText(XLineBreakpoint<JavaFieldBreakpointProperties> breakpoint) {
    return getText(breakpoint, false);
  }

  @Nls
  private static String getText(XBreakpoint<JavaFieldBreakpointProperties> breakpoint, boolean simple) {
    //if (!isValid()) {
    //  return JavaDebuggerBundle.message("status.breakpoint.invalid");
    //}

    JavaFieldBreakpointProperties properties = breakpoint.getProperties();
    String className = properties.myClassName;
    if (className == null || className.isEmpty()) return properties.myFieldName;
    String displayedClassName = simple ? ClassUtil.extractClassName(className) : className;
    return displayedClassName + "." + properties.myFieldName;
  }

  @Nullable
  @Override
  public XBreakpointCustomPropertiesPanel<XLineBreakpoint<JavaFieldBreakpointProperties>> createCustomPropertiesPanel(@NotNull Project project) {
    return new FieldBreakpointPropertiesPanel();
  }

  @Nullable
  @Override
  public JavaFieldBreakpointProperties createProperties() {
    return new JavaFieldBreakpointProperties();
  }

  @Nullable
  @Override
  public JavaFieldBreakpointProperties createBreakpointProperties(@NotNull VirtualFile file, int line) {
    return new JavaFieldBreakpointProperties();
  }

  @Nullable
  @Override
  public XLineBreakpoint<JavaFieldBreakpointProperties> addBreakpoint(Project project, JComponent parentComponent) {
    Ref<XLineBreakpoint<JavaFieldBreakpointProperties>> result = Ref.create(null);
    AddFieldBreakpointDialog dialog = new AddFieldBreakpointDialog(project) {
      @Override
      protected boolean validateData() {
        String className = getClassName();
        if (className.isEmpty()) {
          Messages.showMessageDialog(
            project,
            JavaDebuggerBundle.message("error.field.breakpoint.class.name.not.specified"),
            JavaDebuggerBundle.message("add.field.breakpoint.dialog.title"),
            Messages.getErrorIcon()
          );
          return false;
        }
        String fieldName = getFieldName();
        if (fieldName.isEmpty()) {
          Messages.showMessageDialog(
            project,
            JavaDebuggerBundle.message("error.field.breakpoint.field.name.not.specified"),
            JavaDebuggerBundle.message("add.field.breakpoint.dialog.title"),
            Messages.getErrorIcon()
          );
          return false;
        }
        PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
        if (psiClass == null) {
          Messages.showMessageDialog(
            project,
            JavaDebuggerBundle.message("error.field.breakpoint.class.sources.not.found", className, fieldName, className),
            CommonBundle.getErrorTitle(),
            Messages.getErrorIcon()
          );
          return false;
        }
        PsiFile psiFile = psiClass.getContainingFile();
        Document document = psiFile.getViewProvider().getDocument();
        if (document == null) {
          return false;
        }
        PsiField field = psiClass.findFieldByName(fieldName, false);
        if (field == null) {
          Messages.showMessageDialog(
            project,
            JavaDebuggerBundle.message("error.field.breakpoint.field.not.found", className, fieldName, fieldName),
            CommonBundle.getErrorTitle(),
            Messages.getErrorIcon()
          );
          return false;
        }
        XLineBreakpoint<JavaFieldBreakpointProperties> fieldBreakpoint =
          XDebuggerManager.getInstance(project).getBreakpointManager().addLineBreakpoint(
            JavaFieldBreakpointType.this,
            psiFile.getVirtualFile().getUrl(),
            document.getLineNumber(field.getTextOffset()),
            new JavaFieldBreakpointProperties(fieldName, className)
          );
        result.set(fieldBreakpoint);
        return true;
      }
    };
    dialog.show();
    return result.get();
  }

  @NotNull
  @Override
  public Breakpoint<JavaFieldBreakpointProperties> createJavaBreakpoint(Project project, XBreakpoint breakpoint) {
    return new FieldBreakpoint(project, breakpoint);
  }

  @Override
  public boolean canBeHitInOtherPlaces() {
    return true;
  }

  @Override
  public boolean canPutAt(@NotNull VirtualFile file, int line, @NotNull Project project) {
    return canPutAtElement(file, line, project, (element, document) -> element instanceof PsiField);
  }
}
