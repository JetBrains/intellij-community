package com.intellij.diagnostic.errordialog;

import com.intellij.diagnostic.DiagnosticBundle;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;

/**
 * @author ksafonov
 */
public class CommentsTabForm {
  private final LabeledTextComponent myCommentsArea;
  private JPanel myContentPane;
  private final LabeledTextComponent myErrorComponent;
  private JPanel myErrorPanel;
  private JPanel myCommentsPanel;

  public CommentsTabForm() {
    myErrorComponent = new LabeledTextComponent();
    myErrorComponent.setTitle(DiagnosticBundle.message("error.dialog.error.prompt"));
    
    myCommentsArea = new LabeledTextComponent();
    myCommentsArea.setTitle(DiagnosticBundle.message("error.dialog.comment.prompt"));
    
    JTextArea errorArea = myErrorComponent.getTextComponent();
    //errorArea.setPreferredSize(JBUI.size(IdeErrorsDialog.COMPONENTS_WIDTH, -1));
    errorArea.setLineWrap(true);
    errorArea.setEditable(false);
    errorArea.setBackground(UIUtil.getTextFieldBackground());
    errorArea.setBorder(IdeBorderFactory.createBorder());

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
