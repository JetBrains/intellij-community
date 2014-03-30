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

/*
 * @author max
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.ui.JBCheckboxMenuItem;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.psi.PsiFile;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.GraphicsUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class DaemonEditorPopup extends PopupHandler {
  private final PsiFile myPsiFile;

  public DaemonEditorPopup(final PsiFile psiFile) {
    myPsiFile = psiFile;
  }

  @Override
  public void invokePopup(final Component comp, final int x, final int y) {
    if (ApplicationManager.getApplication() == null) return;
    final JRadioButtonMenuItem errorsFirst = createRadioButtonMenuItem(EditorBundle.message("errors.panel.go.to.errors.first.radio"));
    errorsFirst.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        DaemonCodeAnalyzerSettings.getInstance().NEXT_ERROR_ACTION_GOES_TO_ERRORS_FIRST = errorsFirst.isSelected();
      }
    });
    final JPopupMenu popupMenu = new JBPopupMenu();
    popupMenu.add(errorsFirst);

    final JRadioButtonMenuItem next = createRadioButtonMenuItem(EditorBundle.message("errors.panel.go.to.next.error.warning.radio"));
    next.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        DaemonCodeAnalyzerSettings.getInstance().NEXT_ERROR_ACTION_GOES_TO_ERRORS_FIRST = !next.isSelected();
      }
    });
    popupMenu.add(next);

    ButtonGroup group = new ButtonGroup();
    group.add(errorsFirst);
    group.add(next);

    popupMenu.addSeparator();
    final JMenuItem hLevel = new JBMenuItem(EditorBundle.message("customize.highlighting.level.menu.item"));
    popupMenu.add(hLevel);

    final boolean isErrorsFirst = DaemonCodeAnalyzerSettings.getInstance().NEXT_ERROR_ACTION_GOES_TO_ERRORS_FIRST;
    errorsFirst.setSelected(isErrorsFirst);
    next.setSelected(!isErrorsFirst);
    hLevel.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final PsiFile psiFile = myPsiFile;
        if (psiFile == null) return;
        final HectorComponent component = new HectorComponent(psiFile);
        final Dimension dimension = component.getPreferredSize();
        Point point = new Point(x, y);
        component.showComponent(new RelativePoint(comp, new Point(point.x - dimension.width, point.y)));
      }
    });

    final JBCheckboxMenuItem previewCheckbox = new JBCheckboxMenuItem(IdeBundle.message("checkbox.show.editor.preview.popup"), UISettings.getInstance().SHOW_EDITOR_TOOLTIP);
    popupMenu.addSeparator();
    popupMenu.add(previewCheckbox);
    previewCheckbox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        UISettings.getInstance().SHOW_EDITOR_TOOLTIP = previewCheckbox.isSelected();
        UISettings.getInstance().fireUISettingsChanged();
      }
    });

    PsiFile file = myPsiFile;
    if (file != null && DaemonCodeAnalyzer.getInstance(myPsiFile.getProject()).isHighlightingAvailable(file)) {
      popupMenu.show(comp, x, y);
    }
  }

  private static JRadioButtonMenuItem createRadioButtonMenuItem(final String message) {
    return new JRadioButtonMenuItem(message) {
      @Override
      public void paint(Graphics g) {
        GraphicsUtil.setupAntialiasing(g);
        super.paint(g);
      }
    };
  }
}