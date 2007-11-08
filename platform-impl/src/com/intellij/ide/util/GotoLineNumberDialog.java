package com.intellij.ide.util;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import java.awt.*;

public class GotoLineNumberDialog extends DialogWrapper {
  private JLabel myLabel;
  private JTextField myField;
  private Editor myEditor;

  public GotoLineNumberDialog(Project project, Editor editor){
    super(project, true);
    myEditor = editor;
    setTitle(IdeBundle.message("title.go.to.line"));
    init();
  }

  protected void doOKAction(){
    final LogicalPosition currentPosition = myEditor.getCaretModel().getLogicalPosition();
    int lineNumber = getLineNumber(currentPosition.line + 1);
    if (lineNumber <= 0) return;

    int columnNumber = getColumnNumber(currentPosition.column);
    myEditor.getCaretModel().moveToLogicalPosition(new LogicalPosition(lineNumber - 1, columnNumber - 1));
    myEditor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
    myEditor.getSelectionModel().removeSelection();
    super.doOKAction();
  }

  private int getColumnNumber(int defaultValue) {
    String text = getText();
    int columnIndex = columnSeparatorIndex(text);
    if (columnIndex == -1) return defaultValue;
    try {
      return Integer.parseInt(text.substring(columnIndex + 1));
    } catch (NumberFormatException e) {}
    return defaultValue;
  }

  private static int columnSeparatorIndex(final String text) {
    final int colonIndex = text.indexOf(':');
    return colonIndex >= 0 ? colonIndex : text.indexOf(',');
  }

  private int getLineNumber(int defaultLine) {
    try {
      String text = getText();
      int columnIndex = columnSeparatorIndex(text);
      text = columnIndex == -1 ? text : text.substring(0, columnIndex);
      if (text.trim().length() == 0) return defaultLine;
      return Integer.parseInt(text);
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return myField;
  }

  protected JComponent createCenterPanel() {
    return null;
  }

  private String getText() {
    return myField.getText();
  }

  protected JComponent createNorthPanel() {
    class MyTextField extends JTextField {
      public MyTextField() {
        super("");
      }

      public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(200, d.height);
      }
    }

    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = new Insets(4, 0, 8, 0);
    gbConstraints.fill = GridBagConstraints.VERTICAL;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 1;
    gbConstraints.anchor = GridBagConstraints.EAST;
    myLabel = new JLabel(IdeBundle.message("editbox.line.number"));
    panel.add(myLabel, gbConstraints);

    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    myField = new MyTextField();
    panel.add(myField, gbConstraints);
    myField.setToolTipText(IdeBundle.message("tooltip.syntax.linenumber.columnnumber"));

    return panel;
  }
}
