package com.intellij.diagnostic.errordialog;

import com.intellij.diagnostic.DiagnosticBundle;
import com.intellij.diagnostic.IdeErrorsDialog;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author ksafonov
 */
public class DetailsTabForm {
  private JTextPane myDetailsPane;
  private JPanel myContentPane;
  private LabeledTextComponent myCommentsArea;
  private JPanel myDetailsHolder;
  private JButton myAnalyzeStacktraceButton;

  public DetailsTabForm(@Nullable Action analyzeAction) {
    myCommentsArea.setTitle(DiagnosticBundle.message("error.dialog.comment.prompt"));
    myDetailsPane.setBackground(UIUtil.getTextFieldBackground());
    myDetailsPane.setPreferredSize(new Dimension(IdeErrorsDialog.COMPONENTS_WIDTH, IdeErrorsDialog.INTERNAL_MODE ? 500 : 205));
    myDetailsHolder.setBorder(IdeBorderFactory.createBorder());
    if (analyzeAction != null) {
      myAnalyzeStacktraceButton.setAction(analyzeAction);
    }
    else {
      myAnalyzeStacktraceButton.setVisible(false);
    }
  }

  public void setCommentsAreaVisible(boolean b) {
    myCommentsArea.getContentPane().setVisible(b);
  }

  public void setDetailsText(String s) {
    LabeledTextComponent.setText(myDetailsPane, s, false);
  }

  public void setCommentsText(String s) {
    LabeledTextComponent.setText(myCommentsArea.getTextComponent(), s, true);
  }

  public JPanel getContentPane() {
    return myContentPane;
  }

  public JComponent getPreferredFocusedComponent() {
    if (myCommentsArea.getContentPane().isVisible()) {
      return myCommentsArea.getTextComponent();
    }
    return null;
  }

  public void setCommentsTextEnabled(boolean b) {
    if (myCommentsArea.getContentPane().isVisible()) {
      myCommentsArea.getTextComponent().setEnabled(b);
    }
  }

  public void addCommentsListener(final LabeledTextComponent.TextListener l) {
    myCommentsArea.addCommentsListener(l);
  }

}
