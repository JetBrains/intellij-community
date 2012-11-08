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
package com.intellij.application.options.codeStyle.arrangement.component;

import com.intellij.application.options.codeStyle.arrangement.animation.ArrangementAnimationManager;
import com.intellij.application.options.codeStyle.arrangement.animation.ArrangementAnimationPanel;
import com.intellij.application.options.codeStyle.arrangement.match.ArrangementMatchingRuleEditor;
import com.intellij.application.options.codeStyle.arrangement.match.ArrangementMatchingRulesList;
import com.intellij.application.options.codeStyle.arrangement.util.CalloutBorder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Denis Zhdanov
 * @since 11/7/12 6:19 PM
 */
public class ArrangementEditorComponent implements ArrangementRepresentationAware, ArrangementAnimationManager.Callback {

  @NotNull private final ArrangementMatchingRulesList  myList;
  @NotNull private final ArrangementAnimationPanel     myRenderer;
  @NotNull private final Insets                        myBorderInsets;
  @NotNull private final ArrangementMatchingRuleEditor myEditor;

  private final int myRow;

  public ArrangementEditorComponent(@NotNull ArrangementMatchingRulesList list, int row, @NotNull ArrangementMatchingRuleEditor editor) {
    myList = list;
    myRow = row;
    myEditor = editor;
    JPanel borderPanel = new JPanel(new BorderLayout());
    borderPanel.setBackground(UIUtil.getListBackground());
    borderPanel.add(editor);
    CalloutBorder border = new CalloutBorder();
    borderPanel.setBorder(border);
    myBorderInsets = border.getBorderInsets(borderPanel);
    myRenderer = new ArrangementAnimationPanel(borderPanel);
    new ArrangementAnimationManager(myRenderer, this);
  }

  @NotNull
  @Override
  public JComponent getRenderer() {
    return myRenderer;
  }

  public void expand() {
    myRenderer.startAnimation(true, false);
    myList.repaintRows(myRow, myRow, false);
  }

  @Override
  public void onAnimationIteration(boolean finished) {
    myList.repaintRows(myRow, myRow, false);
  }

  public void applyAvailableWidth(int width) {
    myEditor.applyAvailableWidth(width - myBorderInsets.left - myBorderInsets.right);
  }
}
