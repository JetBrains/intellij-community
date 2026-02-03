// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement.match;

import com.intellij.application.options.codeStyle.arrangement.animation.ArrangementAnimationManager;
import com.intellij.application.options.codeStyle.arrangement.ui.ArrangementEditorAware;
import com.intellij.application.options.codeStyle.arrangement.ui.ArrangementRepresentationAware;
import com.intellij.application.options.codeStyle.arrangement.util.CalloutBorder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public final class ArrangementEditorComponent implements ArrangementRepresentationAware, ArrangementAnimationManager.Callback,
                                                         ArrangementEditorAware
{

  private final @NotNull ArrangementMatchingRulesControl myList;
  private final @NotNull JComponent                      myComponent;
  private final @NotNull Insets                          myBorderInsets;
  private final @NotNull ArrangementMatchingRuleEditor   myEditor;

  private final int myRow;

  public ArrangementEditorComponent(@NotNull ArrangementMatchingRulesControl list, int row, @NotNull ArrangementMatchingRuleEditor editor) {
    myList = list;
    myRow = row;
    myEditor = editor;
    JPanel borderPanel = new JPanel(new BorderLayout()) {
      @Override
      public String toString() {
        return "callout border panel for " + myEditor;
      }
    };
    borderPanel.setBackground(UIUtil.getListBackground());
    borderPanel.add(editor);
    CalloutBorder border = new CalloutBorder();
    borderPanel.setBorder(border);
    myBorderInsets = border.getBorderInsets(borderPanel);
    myComponent = borderPanel;
    myList.repaintRows(myRow, myList.getModel().getSize() - 1, true);
    //myComponent = new ArrangementAnimationPanel(borderPanel, true, false);
  }

  @Override
  public @NotNull JComponent getComponent() {
    return myComponent;
  }

  public void expand() {
    //new ArrangementAnimationManager(myComponent, this).startAnimation();
  }

  @Override
  public void onAnimationIteration(boolean finished) {
    myList.repaintRows(myRow, myList.getModel().getSize() - 1, false);
  }

  public void applyAvailableWidth(int width) {
    myEditor.applyAvailableWidth(width - myBorderInsets.left - myBorderInsets.right);
  }
}
