/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.debugger.actions;

import com.intellij.debugger.ui.breakpoints.BreakpointFactory;
import com.intellij.debugger.ui.breakpoints.BreakpointPropertiesPanel;
import com.intellij.debugger.ui.breakpoints.BreakpointWithHighlighter;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.impl.actions.EditBreakpointActionHandler;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointsDialogFactory;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class JavaEditBreakpointActionHandler extends EditBreakpointActionHandler {
  @Override
  protected void doShowPopup(final Project project, final JComponent component, final Point whereToShow, final Object breakpoint) {
    if (!(breakpoint instanceof BreakpointWithHighlighter)) {
      return;
    }

    final BreakpointWithHighlighter javaBreakpoint = (BreakpointWithHighlighter)breakpoint;
    BreakpointFactory breakpointFactory = null;
    for (BreakpointFactory factory : BreakpointFactory.EXTENSION_POINT_NAME.getExtensions()) {
      if (factory.getBreakpointCategory().equals(javaBreakpoint.getCategory())) {
        breakpointFactory = factory;
      }
    }
    assert breakpointFactory != null : "can't find factory for breakpoint " + javaBreakpoint;

    final BreakpointPropertiesPanel propertiesPanel = breakpointFactory.createBreakpointPropertiesPanel(project, true);
    assert propertiesPanel != null;
    propertiesPanel.initFrom(javaBreakpoint, false);

    final JComponent mainPanel = propertiesPanel.getPanel();
    final JBPopupListener saveOnClose = new JBPopupListener.Adapter() {
      @Override
      public void onClosed(LightweightWindowEvent event) {
        propertiesPanel.saveTo(javaBreakpoint, EmptyRunnable.getInstance());
      }
    };

    final Runnable showMoreOptions = new Runnable() {
      @Override
      public void run() {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            BreakpointsDialogFactory.getInstance(project).showDialog(javaBreakpoint);
          }
        });
      }
    };

    final Balloon balloon = DebuggerUIUtil.showBreakpointEditor(project, mainPanel, whereToShow, component, showMoreOptions, breakpoint);
    balloon.addListener(saveOnClose);

    propertiesPanel.setDelegate(new BreakpointPropertiesPanel.Delegate() {
      @Override
      public void showActionsPanel() {
        propertiesPanel.setActionsPanelVisible(true);
        balloon.hide();
        DebuggerUIUtil.showBreakpointEditor(project, mainPanel, whereToShow, component, showMoreOptions, breakpoint).addListener(saveOnClose);
      }
    });

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        IdeFocusManager.findInstance().requestFocus(mainPanel, true);
      }
    });
  }

  @Override
  public boolean isEnabled(@NotNull Project project, AnActionEvent event) {
    DataContext dataContext = event.getDataContext();
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      return false;
    }
    final Pair<GutterIconRenderer,Object> pair = XBreakpointUtil.findSelectedBreakpoint(project, editor);
    return pair.first != null && pair.second instanceof BreakpointWithHighlighter;
  }
}
