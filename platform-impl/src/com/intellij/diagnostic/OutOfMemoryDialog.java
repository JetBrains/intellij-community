package com.intellij.diagnostic;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class OutOfMemoryDialog extends DialogWrapper {
  public enum MemoryKind {
    HEAP, PERM_GEN
  }

  private MemoryKind myMemoryKind;

  private JPanel myContentPane;
  private JLabel myMessageLabel;
  private JTextField myHeapSizeField;
  private JTextField myPermGenSizeField;
  private JLabel myHeapCurrentValueLabel;
  private JLabel myPermGenCurrentValueLabel;
  private JLabel myHeapSizeLabel;
  private JLabel myPermGenSizeLabel;
  private JLabel myHeapUnitLabel;
  private JLabel myPermGenUnitLabel;
  private JLabel mySettingsFileHintLabel;
  private Action myIgnoreAction;
  private Action myShutdownAction;

  public OutOfMemoryDialog(MemoryKind memoryKind) {
    super(false);
    myMemoryKind = memoryKind;
    setTitle(DiagnosticBundle.message("diagnostic.out.of.memory.title"));

    myMessageLabel.setIcon(Messages.getErrorIcon());
    myMessageLabel.setText(DiagnosticBundle.message(
        "diagnostic.out.of.memory.error",
        memoryKind == MemoryKind.HEAP ? VMOptions.XMX_OPTION_NAME : VMOptions.PERM_GEN_OPTION_NAME,
        ApplicationNamesInfo.getInstance().getProductName()));

    mySettingsFileHintLabel.setText(DiagnosticBundle.message("diagnostic.out.of.memory.willBeSavedTo",
                                                             VMOptions.getSettingsFilePath()));

    myIgnoreAction = new AbstractAction(DiagnosticBundle.message("diagnostic.out.of.memory.ignore")) {
      public void actionPerformed(ActionEvent e) {
        save();
        close(0);
      }
    };

    myShutdownAction = new AbstractAction(DiagnosticBundle.message("diagnostic.out.of.memory.shutdown")) {
      public void actionPerformed(ActionEvent e) {
        save();
        System.exit(0);
      }
    };
    myShutdownAction.putValue(DialogWrapper.DEFAULT_ACTION, true);

    myHeapSizeLabel.setText(VMOptions.XMX_OPTION_NAME);
    myHeapSizeField.setText(String.valueOf(VMOptions.readXmx()));
    myHeapCurrentValueLabel.setText(DiagnosticBundle.message("diagnostic.out.of.memory.currentValue", VMOptions.readXmx()));

    myPermGenSizeLabel.setText(VMOptions.PERM_GEN_OPTION_NAME);
    myPermGenSizeField.setText(String.valueOf(VMOptions.readMaxPermGen()));
    myPermGenCurrentValueLabel.setText(DiagnosticBundle.message("diagnostic.out.of.memory.currentValue", VMOptions.readMaxPermGen()));

    if (memoryKind == MemoryKind.HEAP) {
      myHeapSizeLabel.setForeground(Color.RED);
      myHeapSizeField.setForeground(Color.RED);
      myHeapUnitLabel.setForeground(Color.RED);
      myHeapCurrentValueLabel.setForeground(Color.RED);
    }
    else {
      myPermGenSizeLabel.setForeground(Color.RED);
      myPermGenSizeField.setForeground(Color.RED);
      myPermGenUnitLabel.setForeground(Color.RED);
      myPermGenCurrentValueLabel.setForeground(Color.RED);
    }

    init();
  }

  private void save() {
    try {
      VMOptions.writeXmx(Integer.parseInt(myHeapSizeField.getText()));
    }
    catch (NumberFormatException e) {
    }

    try {
      VMOptions.writeMaxPermGen(Integer.parseInt(myPermGenSizeField.getText()));
    }
    catch (NumberFormatException e) {
    }
  }

  protected JComponent createCenterPanel() {
    return myContentPane;
  }

  protected Action[] createActions() {
    return new Action[]{myShutdownAction, myIgnoreAction};
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myMemoryKind == MemoryKind.HEAP ? myHeapSizeField : myPermGenSizeField;
  }
}
