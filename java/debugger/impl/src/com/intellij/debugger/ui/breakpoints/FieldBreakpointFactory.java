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

import com.intellij.CommonBundle;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.HelpID;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jdom.Element;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 26, 2005
 */
public class FieldBreakpointFactory extends BreakpointFactory{
  public Breakpoint createBreakpoint(Project project, final Element element) {
    return new FieldBreakpoint(project);
  }

  public Icon getIcon() {
    return AllIcons.Debugger.Db_field_breakpoint;
  }

  public Icon getDisabledIcon() {
    return AllIcons.Debugger.Db_disabled_field_breakpoint;
  }

  @Override
  protected String getHelpID() {
    return HelpID.FIELD_WATCHPOINTS;
  }

  @Override
  public String getDisplayName() {
    return DebuggerBundle.message("field.watchpoints.tab.title");
  }

  @Override
  public BreakpointPropertiesPanel createBreakpointPropertiesPanel(Project project, boolean compact) {
    return new FieldBreakpointPropertiesPanel(project, compact);
  }

  public Key<FieldBreakpoint> getBreakpointCategory() {
    return FieldBreakpoint.CATEGORY;
  }

  @Override
  public Breakpoint addBreakpoint(final Project project) {
    final Ref<Breakpoint> result = Ref.create(null);
    AddFieldBreakpointDialog dialog = new AddFieldBreakpointDialog(project) {
      protected boolean validateData() {
        String className = getClassName();
        if (className.length() == 0) {
          Messages.showMessageDialog(project, DebuggerBundle.message("error.field.breakpoint.class.name.not.specified"),
                                     DebuggerBundle.message("add.field.breakpoint.dialog.title"), Messages.getErrorIcon());
          return false;
        }
        String fieldName = getFieldName();
        if (fieldName.length() == 0) {
          Messages.showMessageDialog(project, DebuggerBundle.message("error.field.breakpoint.field.name.not.specified"),
                                     DebuggerBundle.message("add.field.breakpoint.dialog.title"), Messages.getErrorIcon());
          return false;
        }
        PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
        if (psiClass != null) {
          PsiFile psiFile  = psiClass.getContainingFile();
          Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
          if(document != null) {
            PsiField field = psiClass.findFieldByName(fieldName, true);
            if(field != null) {
              int line = document.getLineNumber(field.getTextOffset());
              FieldBreakpoint fieldBreakpoint = DebuggerManagerEx.getInstanceEx(project).getBreakpointManager().addFieldBreakpoint(document, line, fieldName);
              if (fieldBreakpoint != null) {
                result.set(fieldBreakpoint);
                return true;
              }
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

  @Override
  public boolean canAddBreakpoints() {
    return true;
  }
}
