package com.intellij.diagnostic.errordialog;

import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;
import java.awt.*;

/**
 * @author ksafonov
 */
public class LabeledTextComponent {
  public interface TextListener {
    void textChanged(String newText);
  }

  private LabeledComponent<JPanel> myComponent;
  private JPanel myContentPane;

  private final JTextArea myTextPane;

  public LabeledTextComponent() {
    myTextPane = new JTextArea();

    myComponent.getLabel().setMinimumSize(new Dimension(0, -1));
    myComponent.getComponent().setLayout(new BorderLayout());
    myTextPane.setBackground(UIUtil.getTextFieldBackground());
    myComponent.getComponent().add(new JBScrollPane(myTextPane));
    myComponent.getComponent().setBorder(IdeBorderFactory.createBorder());
  }

  public void setTitle(String title) {
    myComponent.setText(title);
  }

  public JPanel getContentPane() {
    return myContentPane;
  }

  public JTextArea getTextComponent() {
    return myTextPane;
  }

  static void setText(JTextComponent pane, String text, boolean caretToTheEnd) {
    pane.setText(text);
    if (text != null && !caretToTheEnd && pane.getCaret() != null) {
      // Upon some strange circumstances caret may be missing from the text component making the following line fail with NPE.
      pane.setCaretPosition(0);
    }
  }

  public void addCommentsListener(final TextListener l) {
    myTextPane.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        l.textChanged(myTextPane.getText());
      }
    });
  }

}
