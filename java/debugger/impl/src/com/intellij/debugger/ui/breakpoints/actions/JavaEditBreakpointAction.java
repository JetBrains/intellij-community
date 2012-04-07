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
package com.intellij.debugger.ui.breakpoints.actions;

import com.intellij.debugger.ui.breakpoints.BreakpointFactory;
import com.intellij.debugger.ui.breakpoints.BreakpointPropertiesPanel;
import com.intellij.debugger.ui.breakpoints.BreakpointWithHighlighter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.xdebugger.impl.actions.EditBreakpointAction;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;

import javax.swing.*;
import java.awt.*;


public class JavaEditBreakpointAction extends EditBreakpointAction {
  private BreakpointWithHighlighter myBreakpointWithHighlighter;

  public JavaEditBreakpointAction(BreakpointWithHighlighter breakpointWithHighlighter, RangeHighlighter highlighter) {
    super(breakpointWithHighlighter, highlighter.getGutterIconRenderer());
    myBreakpointWithHighlighter = breakpointWithHighlighter;
  }

  @Override
  protected void doShowPopup(final Project project, final EditorGutterComponentEx gutterComponent, final Point whereToShow) {
    Key<? extends BreakpointWithHighlighter> category = myBreakpointWithHighlighter.getCategory();
    final BreakpointFactory[] allFactories = ApplicationManager.getApplication().getExtensions(BreakpointFactory.EXTENSION_POINT_NAME);
    BreakpointFactory breakpointFactory = null;
    for (BreakpointFactory factory : allFactories) {
      if (factory.getBreakpointCategory().equals(category)) {
        breakpointFactory = factory;
      }
    }
    assert breakpointFactory != null : "can't find factory for breakpoint " + myBreakpointWithHighlighter;

    final BreakpointPropertiesPanel propertiesPanel = breakpointFactory.createBreakpointPropertiesPanel(project);
    propertiesPanel.initFrom(myBreakpointWithHighlighter, false);

    final JComponent mainPanel = propertiesPanel.getPanel();
    final String displayName = myBreakpointWithHighlighter.getDisplayName();

    final JBPopupListener saveOnClose = new JBPopupListener() {
      @Override
      public void beforeShown(LightweightWindowEvent event) {
      }

      @Override
      public void onClosed(LightweightWindowEvent event) {
        propertiesPanel.saveTo(myBreakpointWithHighlighter, new Runnable() {
          @Override
          public void run() {
          }
        });
      }
    };

    final Runnable showMoreOptions = new Runnable() {
      @Override
      public void run() {
        propertiesPanel.setMoreOptionsVisible(true);
        final Balloon newBalloon = DebuggerUIUtil.showBreakpointEditor(mainPanel, displayName, whereToShow, gutterComponent, null);
        newBalloon.addListener(saveOnClose);
      }
    };
    final Balloon balloon = DebuggerUIUtil.showBreakpointEditor(mainPanel, displayName, whereToShow, gutterComponent, propertiesPanel.isMoreOptionsVisible() ? null : showMoreOptions);
    balloon.addListener(saveOnClose);

    propertiesPanel.setDelegate(new BreakpointPropertiesPanel.Delegate() {
      @Override
      public void showActionsPanel() {
        propertiesPanel.setActionsPanelVisible(true);
        balloon.hide();
        final Balloon newBalloon =
          DebuggerUIUtil.showBreakpointEditor(mainPanel, displayName, whereToShow, gutterComponent, showMoreOptions);
        newBalloon.addListener(saveOnClose);
      }
    });

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        IdeFocusManager.findInstance().requestFocus(mainPanel, true);
      }
    });
  }

}
