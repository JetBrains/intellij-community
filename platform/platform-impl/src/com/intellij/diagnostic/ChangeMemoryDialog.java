// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;

public class ChangeMemoryDialog extends DialogWrapper {

  final ApplicationEx app = (ApplicationEx)ApplicationManager.getApplication();
  final boolean isRestartCapable = app.isRestartCapable();
  private final Action myIgnoreAction;
  private final Action myShutdownAction;
  private JPanel myContentPane;
  private JLabel myMessageLabel;
  private JTextField myHeapSizeField;
  private JBLabel myHeapCurrentValueLabel;
  private JBLabel mySettingsFileHintLabel;

  public ChangeMemoryDialog(long unusedMemory, long totalMemory) {
    this();
    mySettingsFileHintLabel.setText(IdeBundle.message("change.memory.usage", unusedMemory / 1024 / 1024, totalMemory / 1024 / 1024));
  }

  public ChangeMemoryDialog() {
    super(false);
    setTitle(IdeBundle.message("change.memory.title"));

    mySettingsFileHintLabel.setIcon(Messages.getWarningIcon());

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

    File file = VMOptions.getWriteFile();
    if (file != null) {
      mySettingsFileHintLabel.setText(DiagnosticBundle.message("diagnostic.out.of.memory.willBeSavedTo", file.getPath()));
      myMessageLabel.setText(IdeBundle.message("change.memory.restart"));

      int newMemory;
      if (SystemInfo.is64Bit) {
        newMemory = Math.min(2048, Math.round(currentMemory * 1.5f));
      }
      else {
        newMemory = Math.min(800, Math.round(currentMemory * 1.5f));
      }
      myHeapSizeField.setText(String.valueOf(newMemory));
    }
    else {
      myMessageLabel.setText(IdeBundle.message("change.memory.nofile"));
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

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{myShutdownAction, myIgnoreAction};
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myHeapSizeField;
  }

  private class SaveAction extends AbstractAction {
    public SaveAction(String actionName) {
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
          mySettingsFileHintLabel.setText(IdeBundle.message("change.memory.low"));
          return false;
        }
        if (heapSize > 800 && !SystemInfo.is64Bit) {
          mySettingsFileHintLabel.setText(IdeBundle.message("change.memory.large"));
          return false;
        }
        VMOptions.writeOption(VMOptions.MemoryKind.HEAP, heapSize);
        return true;
      }
      catch (NumberFormatException ignored) {
        mySettingsFileHintLabel.setText(IdeBundle.message("change.memory.integer"));
        return false;
      }
    }
  }
}
