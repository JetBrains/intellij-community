// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic.errordialog;

import com.intellij.diagnostic.DiagnosticBundle;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;

/**
 * @author ksafonov
 */
public class CommentsTabForm {
  private final LabeledTextComponent myErrorComponent;
  private final LabeledTextComponent myCommentsArea;
  private JPanel myContentPane;
  private JPanel myErrorPanel;
  private JPanel myCommentsPanel;

  public CommentsTabForm() {
    myErrorComponent = new LabeledTextComponent();
    myErrorComponent.setTitle(DiagnosticBundle.message("error.dialog.error.prompt"));
    JTextArea errorArea = myErrorComponent.getTextComponent();
    errorArea.setLineWrap(true);
    errorArea.setEditable(false);
    errorArea.setBackground(UIUtil.getTextFieldBackground());
    errorArea.setBorder(IdeBorderFactory.createBorder());

    myCommentsArea = new LabeledTextComponent();
    myCommentsArea.setTitle(DiagnosticBundle.message("error.dialog.comment.prompt"));
    myCommentsArea.getTextComponent().setLineWrap(true);

    myErrorPanel.add(myErrorComponent.getContentPane());
    myCommentsPanel.add(myCommentsArea.getContentPane());
  }

  public JPanel getContentPane() {
    return myContentPane;
  }

  public void setErrorText(String s) {
    myErrorComponent.getTextComponent().setText(s);
    myErrorComponent.getTextComponent().setCaretPosition(0);
  }

  public void setCommentText(String s) {
    LabeledTextComponent.setText(myCommentsArea.getTextComponent(), s, true);
  }

  public JComponent getPreferredFocusedComponent() {
    return myCommentsArea.getTextComponent();
  }

  public void setCommentsTextEnabled(boolean b) {
    myCommentsArea.getTextComponent().setEnabled(b);
  }

  public void addCommentsListener(final LabeledTextComponent.TextListener l) {
    myCommentsArea.addCommentsListener(l);
  }
}