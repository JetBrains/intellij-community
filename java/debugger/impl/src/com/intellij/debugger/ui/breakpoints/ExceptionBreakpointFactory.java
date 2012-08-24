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

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.HelpID;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.ui.breakpoints.actions.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Key;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.search.GlobalSearchScope;
import org.jdom.Element;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 26, 2005
 */
public class ExceptionBreakpointFactory extends BreakpointFactory {
  public Breakpoint createBreakpoint(Project project, final Element element) {
    return new ExceptionBreakpoint(project);
  }

  public Icon getIcon() {
    return AllIcons.Debugger.Db_exception_breakpoint;
  }

  public Icon getDisabledIcon() {
    return AllIcons.Debugger.Db_disabled_exception_breakpoint;
  }

  @Override
  protected String getHelpID() {
    return HelpID.EXCEPTION_BREAKPOINTS;
  }

  @Override
  public String getDisplayName() {
    return DebuggerBundle.message("exception.breakpoints.tab.title");
  }

  @Override
  protected void configureBreakpointPanel(BreakpointPanel panel) {
    super.configureBreakpointPanel(panel);
    panel.getTree().setGroupByMethods(false);
  }

  @Override
  public BreakpointPropertiesPanel createBreakpointPropertiesPanel(Project project, boolean compact) {
    return new ExceptionBreakpointPropertiesPanel(project, compact);
  }

  @Override
  protected BreakpointPanelAction[] createBreakpointPanelActions(final Project project, DialogWrapper parentDialog) {
    return new BreakpointPanelAction[]{
      new SwitchViewAction(),
      new AddAction(this, project),
      new RemoveAction(project) {
      public void update() {
        super.update();
        if (getButton().isEnabled()) {
          Breakpoint[] selectedBreakpoints = getPanel().getSelectedBreakpoints();
          for (Breakpoint bp : selectedBreakpoints) {
            if (bp instanceof AnyExceptionBreakpoint) {
              getButton().setEnabled(false);
            }
          }
        }
      }
    }, new ToggleGroupByClassesAction(), new ToggleFlattenPackagesAction(),};
  }

  public BreakpointPanel createBreakpointPanel(final Project project, final DialogWrapper parentDialog) {
    BreakpointPanel panel =
      new BreakpointPanel(project, createBreakpointPropertiesPanel(project, false), createBreakpointPanelActions(project, parentDialog),
                          getBreakpointCategory(), getDisplayName(), getHelpID()) {
        public void resetBreakpoints() {
          super.resetBreakpoints();
          Breakpoint[] breakpoints = getBreakpointManager().getBreakpoints(getBreakpointCategory());
          final AnyExceptionBreakpoint anyExceptionBreakpoint =
            DebuggerManagerEx.getInstanceEx(project).getBreakpointManager().getAnyExceptionBreakpoint();
          boolean found = false;
          for (Breakpoint breakpoint : breakpoints) {
            if (breakpoint.equals(anyExceptionBreakpoint)) {
              found = true;
              break;
            }
          }
          if (!found) {
            insertBreakpointAt(anyExceptionBreakpoint, 0);
          }
        }
      };
    configureBreakpointPanel(panel);
    return panel;
  }

  public Key<ExceptionBreakpoint> getBreakpointCategory() {
    return ExceptionBreakpoint.CATEGORY;
  }

  @Override
  public boolean canAddBreakpoints() {
    return true;
  }

  @Override
  public Breakpoint addBreakpoint(Project project) {
    ExceptionBreakpoint breakpoint = null;
    final PsiClass throwableClass =
      JavaPsiFacade.getInstance(project).findClass("java.lang.Throwable", GlobalSearchScope.allScope(project));
    TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project)
      .createInheritanceClassChooser(DebuggerBundle.message("add.exception.breakpoint.classchooser.title"),
                                     GlobalSearchScope.allScope(project), throwableClass, true, true, null);
    chooser.showDialog();
    PsiClass selectedClass = chooser.getSelected();
    String qName = selectedClass == null ? null : JVMNameUtil.getNonAnonymousClassName(selectedClass);

    if (qName != null && qName.length() > 0) {
      breakpoint = DebuggerManagerEx.getInstanceEx(project).getBreakpointManager()
        .addExceptionBreakpoint(qName, ((PsiClassOwner)selectedClass.getContainingFile()).getPackageName());
    }
    return breakpoint;
  }
}
