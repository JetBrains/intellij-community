// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.SmartList;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xml.CommonXmlStrings;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaFieldBreakpointProperties;

import javax.swing.*;
import java.util.List;

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

  @Override
  public @NotNull Icon getEnabledIcon() {
    return AllIcons.Debugger.Db_field_breakpoint;
  }

  @Override
  public @NotNull Icon getDisabledIcon() {
    return AllIcons.Debugger.Db_disabled_field_breakpoint;
  }

  @Override
  public @NotNull Icon getSuspendNoneIcon() {
    return AllIcons.Debugger.Db_no_suspend_field_breakpoint;
  }

  @Override
  public @NotNull Icon getMutedEnabledIcon() {
    return AllIcons.Debugger.Db_muted_field_breakpoint;
  }

  @Override
  public @NotNull Icon getMutedDisabledIcon() {
    return AllIcons.Debugger.Db_muted_disabled_field_breakpoint;
  }

  @Override
  public @NotNull Icon getInactiveDependentIcon() {
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

  @Override
  protected @Nls @NotNull String getGeneralDescription(XLineBreakpointType<JavaFieldBreakpointProperties>.XLineBreakpointVariant variant) {
    return JavaDebuggerBundle.message("field.watchpoint.description");
  }

  @Override
  public @Nls String getGeneralDescription(XLineBreakpoint<JavaFieldBreakpointProperties> breakpoint) {
    return JavaDebuggerBundle.message("field.watchpoint.description");
  }

  @Override
  public List<@Nls String> getPropertyXMLDescriptions(XLineBreakpoint<JavaFieldBreakpointProperties> breakpoint) {
    var res = new SmartList<>(super.getPropertyXMLDescriptions(breakpoint));
    var props = breakpoint.getProperties();
    if (props != null) {
      var defaults = createProperties();
      if (props.WATCH_ACCESS != defaults.WATCH_ACCESS || props.WATCH_MODIFICATION != defaults.WATCH_MODIFICATION) {
        // Add both if at least one property isn't default.
        res.add(JavaDebuggerBundle.message("field.watchpoint.property.name.access") + CommonXmlStrings.NBSP
                + props.WATCH_ACCESS);
        res.add(JavaDebuggerBundle.message("field.watchpoint.property.name.modification") + CommonXmlStrings.NBSP
                + props.WATCH_MODIFICATION);
      }
    }
    return res;
  }

  @Override
  public String getShortText(XLineBreakpoint<JavaFieldBreakpointProperties> breakpoint) {
    return getText(breakpoint, true);
  }

  public @Nls String getText(XLineBreakpoint<JavaFieldBreakpointProperties> breakpoint) {
    return getText(breakpoint, false);
  }

  private static @Nls @NotNull String getText(XBreakpoint<JavaFieldBreakpointProperties> breakpoint, boolean simple) {
    //if (!isValid()) {
    //  return JavaDebuggerBundle.message("status.breakpoint.invalid");
    //}

    JavaFieldBreakpointProperties properties = breakpoint.getProperties();
    String className = properties.myClassName;
    String fieldName = properties.myFieldName;
    if (className == null || className.isEmpty()) {
      if (fieldName == null || fieldName.isEmpty()) {
        // TODO: what to do in the case when JavaFieldBreakpointProperties are not initialized yet?
        return JavaDebuggerBundle.message("field.watchpoint.description");
      }
      return fieldName;
    }
    String displayedClassName = simple ? ClassUtil.extractClassName(className) : className;
    return displayedClassName + "." + fieldName;
  }

  @Override
  public @Nullable XBreakpointCustomPropertiesPanel<XLineBreakpoint<JavaFieldBreakpointProperties>> createCustomPropertiesPanel(@NotNull Project project) {
    return new FieldBreakpointPropertiesPanel();
  }

  @Override
  public @Nullable JavaFieldBreakpointProperties createProperties() {
    return new JavaFieldBreakpointProperties();
  }

  @Override
  public @Nullable JavaFieldBreakpointProperties createBreakpointProperties(@NotNull VirtualFile file, int line) {
    return createProperties();
  }

  @Override
  public @Nullable XLineBreakpoint<JavaFieldBreakpointProperties> addBreakpoint(Project project, JComponent parentComponent) {
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

  @Override
  public @NotNull Breakpoint<JavaFieldBreakpointProperties> createJavaBreakpoint(Project project, XBreakpoint breakpoint) {
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
