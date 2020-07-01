// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.nio.file.Path;

public class EditXmxVMOptionDialog extends DialogWrapper {

  private static final int MAX_SUGGESTED_HEAP_SIZE = Registry.intValue("max.suggested.heap.size");
  final ApplicationEx app = (ApplicationEx)ApplicationManager.getApplication();
  final boolean isRestartCapable = app.isRestartCapable();
  private final Action myIgnoreAction;
  private final Action myShutdownAction;
  private JPanel myContentPane;
  private JLabel myMessageLabel;
  private JTextField myHeapSizeField;
  private JBLabel myHeapCurrentValueLabel;
  private JBLabel mySettingsFileHintLabel;

  public EditXmxVMOptionDialog(long unusedMemory, long totalMemory) {
    this();
    mySettingsFileHintLabel.setText(DiagnosticBundle.message("change.memory.usage", unusedMemory / 1024 / 1024, totalMemory / 1024 / 1024));
  }

  public EditXmxVMOptionDialog() {
    super(false);
    setTitle(DiagnosticBundle.message("change.memory.title"));

    mySettingsFileHintLabel.setIcon(AllIcons.General.Warning);

    myIgnoreAction = new AbstractAction("Close") {
      @Override
      public void actionPerformed(ActionEvent e) {
        close(0);
      }
    };

    if (isRestartCapable) {
      myShutdownAction = new SaveAction("Save and Restart");
    }
    else {
      myShutdownAction = new SaveAction("Save");
    }
    myShutdownAction.putValue(DialogWrapper.DEFAULT_ACTION, true);

    int currentMemory = VMOptions.readOption(VMOptions.MemoryKind.HEAP, true);
    String formatted =
      currentMemory == -1 ? DiagnosticBundle.message("diagnostic.out.of.memory.currentValue.unknown") : String.valueOf(currentMemory);
    myHeapCurrentValueLabel.setText(DiagnosticBundle.message("diagnostic.out.of.memory.currentValue", formatted));

    Path file = VMOptions.getWriteFile();
    if (file != null) {
      mySettingsFileHintLabel.setText(DiagnosticBundle.message("diagnostic.out.of.memory.willBeSavedTo", file.toString()));
      myMessageLabel.setText(DiagnosticBundle.message("change.memory.restart"));

      int newMemory;
      if (SystemInfo.is64Bit) {
        newMemory = Math.min(MAX_SUGGESTED_HEAP_SIZE, Math.round(currentMemory * 1.5f));
      }
      else {
        newMemory = Math.min(800, Math.round(currentMemory * 1.5f));
      }
      myHeapSizeField.setText(String.valueOf(newMemory));
    }
    else {
      myMessageLabel.setText(DiagnosticBundle.message("change.memory.nofile"));
      mySettingsFileHintLabel.setVisible(false);
      myHeapSizeField.setEnabled(false);
      myShutdownAction.setEnabled(false);
    }

    init();
  }


  @Override
  protected JComponent createCenterPanel() {
    return myContentPane;
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{myShutdownAction, myIgnoreAction};
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myHeapSizeField;
  }

  private class SaveAction extends AbstractAction {
    SaveAction(String actionName) {
      super(actionName);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      boolean success = save();
      if (success) {
        if (isRestartCapable) {
          app.restart(true);
        }
        else {
          close(0);
        }
      }
    }

    private boolean save() {
      try {
        int heapSize = Integer.parseInt(myHeapSizeField.getText());
        if (heapSize < 256) {
          mySettingsFileHintLabel.setText(DiagnosticBundle.message("change.memory.low"));
          return false;
        }
        if (heapSize > 800 && !SystemInfo.is64Bit) {
          mySettingsFileHintLabel.setText(DiagnosticBundle.message("change.memory.large"));
          return false;
        }
        VMOptions.writeOption(VMOptions.MemoryKind.HEAP, heapSize);
        return true;
      }
      catch (NumberFormatException ignored) {
        mySettingsFileHintLabel.setText(DiagnosticBundle.message("change.memory.integer"));
        return false;
      }
    }
  }
}
