/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.CommonBundle;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.HelpID;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaFieldBreakpointProperties;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 */
public class JavaFieldBreakpointType extends JavaLineBreakpointTypeBase<JavaFieldBreakpointProperties>
                                     implements JavaBreakpointType<JavaFieldBreakpointProperties> {

  public JavaFieldBreakpointType() {
    super("java-field", DebuggerBundle.message("field.watchpoints.tab.title"));
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
  public Icon getMutedEnabledIcon() {
    return AllIcons.Debugger.Db_muted_field_breakpoint;
  }

  @NotNull
  @Override
  public Icon getMutedDisabledIcon() {
    return AllIcons.Debugger.Db_muted_disabled_field_breakpoint;
  }

  //@Override
  protected String getHelpID() {
    return HelpID.FIELD_WATCHPOINTS;
  }

  //@Override
  public String getDisplayName() {
    return DebuggerBundle.message("field.watchpoints.tab.title");
  }

  @Override
  public String getShortText(XLineBreakpoint<JavaFieldBreakpointProperties> breakpoint) {
    return getText(breakpoint);
  }

  public String getText(XLineBreakpoint<JavaFieldBreakpointProperties> breakpoint) {
    //if(!isValid()) {
    //  return DebuggerBundle.message("status.breakpoint.invalid");
    //}

    JavaFieldBreakpointProperties properties = breakpoint.getProperties();
    final String className = properties.myClassName;
    return className != null && !className.isEmpty() ? className + "." + properties.myFieldName : properties.myFieldName;
  }

  @Nullable
  @Override
  public XBreakpointCustomPropertiesPanel<XLineBreakpoint<JavaFieldBreakpointProperties>> createCustomPropertiesPanel() {
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
  public XLineBreakpoint<JavaFieldBreakpointProperties> addBreakpoint(final Project project, JComponent parentComponent) {
    final Ref<XLineBreakpoint<JavaFieldBreakpointProperties>> result = Ref.create(null);
    AddFieldBreakpointDialog dialog = new AddFieldBreakpointDialog(project) {
      protected boolean validateData() {
        final String className = getClassName();
        if (className.length() == 0) {
          Messages.showMessageDialog(project, DebuggerBundle.message("error.field.breakpoint.class.name.not.specified"),
                                     DebuggerBundle.message("add.field.breakpoint.dialog.title"), Messages.getErrorIcon());
          return false;
        }
        final String fieldName = getFieldName();
        if (fieldName.length() == 0) {
          Messages.showMessageDialog(project, DebuggerBundle.message("error.field.breakpoint.field.name.not.specified"),
                                     DebuggerBundle.message("add.field.breakpoint.dialog.title"), Messages.getErrorIcon());
          return false;
        }
        PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
        if (psiClass != null) {
          final PsiFile psiFile  = psiClass.getContainingFile();
          Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
          if(document != null) {
            PsiField field = psiClass.findFieldByName(fieldName, true);
            if(field != null) {
              final int line = document.getLineNumber(field.getTextOffset());
              WriteAction.run(() -> {
                XLineBreakpoint<JavaFieldBreakpointProperties> fieldBreakpoint = XDebuggerManager.getInstance(project).getBreakpointManager()
                  .addLineBreakpoint(JavaFieldBreakpointType.this, psiFile.getVirtualFile().getUrl(), line, new JavaFieldBreakpointProperties(fieldName, className));
                result.set(fieldBreakpoint);
              });
              return true;
            }
            else {
              Messages.showMessageDialog(project,
                                         DebuggerBundle.message("error.field.breakpoint.field.not.found", className, fieldName, fieldName),
                                         CommonBundle.getErrorTitle(),
                                         Messages.getErrorIcon()
              );
            }
          }
        } else {
          Messages.showMessageDialog(project,
                                     DebuggerBundle.message("error.field.breakpoint.class.sources.not.found", className, fieldName, className),
                                     CommonBundle.getErrorTitle(),
                                     Messages.getErrorIcon()
          );
        }
        return false;
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
}
