/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.diagnostic;

import com.intellij.diagnostic.VMOptions.MemoryKind;
import com.intellij.ide.IdeBundle;
import com.intellij.idea.Main;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.MemoryDumpHelper;
import com.intellij.util.SystemProperties;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;

public class OutOfMemoryDialog extends DialogWrapper {
  private final MemoryKind myMemoryKind;

  private JPanel myContentPane;
  private JBLabel myIconLabel;
  private JBLabel myMessageLabel;
  private JBLabel myHeapSizeLabel;
  private JTextField myHeapSizeField;
  private JBLabel myHeapUnitsLabel;
  private JBLabel myHeapCurrentValueLabel;
  private JBLabel myPermGenSizeLabel;
  private JTextField myPermGenSizeField;
  private JBLabel myPermGenUnitsLabel;
  private JBLabel myPermGenCurrentValueLabel;
  private JBLabel myCodeCacheSizeLabel;
  private JTextField myCodeCacheSizeField;
  private JBLabel myCodeCacheUnitsLabel;
  private JBLabel myCodeCacheCurrentValueLabel;
  private JBLabel mySettingsFileHintLabel;
  private JBLabel myDumpMessageLabel;
  private final Action myContinueAction;
  private final Action myShutdownAction;
  private final Action myHeapDumpAction;

  public OutOfMemoryDialog(@NotNull MemoryKind memoryKind) {
    super(false);
    myMemoryKind = memoryKind;
    setTitle(DiagnosticBundle.message("diagnostic.out.of.memory.title"));

    myIconLabel.setIcon(Messages.getErrorIcon());
    myMessageLabel.setText(DiagnosticBundle.message("diagnostic.out.of.memory.error", memoryKind.optionName));
    myMessageLabel.setBorder(IdeBorderFactory.createEmptyBorder(0, 0, 10, 0));

    File file = VMOptions.getWriteFile();
    if (file != null) {
      mySettingsFileHintLabel.setText(DiagnosticBundle.message("diagnostic.out.of.memory.willBeSavedTo", file.getPath()));
      mySettingsFileHintLabel.setBorder(IdeBorderFactory.createEmptyBorder(10, 0, 0, 0));
    }
    else {
      mySettingsFileHintLabel.setVisible(false);
      myHeapSizeField.setEnabled(false);
      myPermGenSizeField.setEnabled(false);
      myCodeCacheSizeField.setEnabled(false);
    }

    myContinueAction = new DialogWrapperAction(DiagnosticBundle.message("diagnostic.out.of.memory.continue")) {
      @Override
      protected void doAction(ActionEvent e) {
        save();
        close(0);
      }
    };

    myShutdownAction = new DialogWrapperAction(IdeBundle.message("ide.shutdown.action")) {
      @Override
      protected void doAction(ActionEvent e) {
        save();
        System.exit(Main.OUT_OF_MEMORY);
      }
    };
    myShutdownAction.putValue(DialogWrapper.DEFAULT_ACTION, true);

    boolean heapDump = memoryKind == MemoryKind.HEAP && MemoryDumpHelper.memoryDumpAvailable();
    myHeapDumpAction = !heapDump ? null : new DialogWrapperAction(DiagnosticBundle.message("diagnostic.out.of.memory.dump")) {
      @Override
      protected void doAction(ActionEvent e) {
        snapshot();
      }
    };

    configControls(MemoryKind.HEAP, myHeapSizeLabel, myHeapSizeField, myHeapUnitsLabel, myHeapCurrentValueLabel);
    configControls(MemoryKind.PERM_GEN, myPermGenSizeLabel, myPermGenSizeField, myPermGenUnitsLabel, myPermGenCurrentValueLabel);
    configControls(MemoryKind.CODE_CACHE, myCodeCacheSizeLabel, myCodeCacheSizeField, myCodeCacheUnitsLabel, myCodeCacheCurrentValueLabel);

    init();
  }

  private void configControls(MemoryKind option, JLabel sizeLabel, JTextField sizeField, JLabel unitsLabel, JLabel currentLabel) {
    sizeLabel.setText('-' + option.optionName);

    int effective = VMOptions.readOption(option, true);
    int stored = VMOptions.readOption(option, false);
    if (stored == -1) stored = effective;
    sizeField.setText(format(stored));
    currentLabel.setText(DiagnosticBundle.message("diagnostic.out.of.memory.currentValue", format(effective)));

    if (option == myMemoryKind) {
      sizeLabel.setForeground(JBColor.RED);
      sizeField.setForeground(JBColor.RED);
      unitsLabel.setForeground(JBColor.RED);
      currentLabel.setForeground(JBColor.RED);
    }
  }

  private static String format(int value) {
    return value == -1 ? DiagnosticBundle.message("diagnostic.out.of.memory.currentValue.unknown") : String.valueOf(value);
  }

  private void save() {
    try {
      int heapSize = Integer.parseInt(myHeapSizeField.getText());
      VMOptions.writeXmx(heapSize);
    }
    catch (NumberFormatException ignored) { }

    try {
      int permGenSize = Integer.parseInt(myPermGenSizeField.getText());
      VMOptions.writeMaxPermGen(permGenSize);
    }
    catch (NumberFormatException ignored) { }

    try {
      int codeCacheSize = Integer.parseInt(myCodeCacheSizeField.getText());
      VMOptions.writeCodeCache(codeCacheSize);
    }
    catch (NumberFormatException ignored) { }
  }

  @SuppressWarnings("SSBasedInspection")
  private void snapshot() {
    enableControls(false);
    myDumpMessageLabel.setVisible(true);
    myDumpMessageLabel.setText("Dumping memory...");

    Runnable task = new Runnable() {
      @Override
      public void run() {
        TimeoutUtil.sleep(250);  // to give UI chance to update
        String message = "";
        try {
          String name = ApplicationNamesInfo.getInstance().getLowercaseProductName();
          String path = SystemProperties.getUserHome() + File.separator + "heapDump-" + name + '-' + System.currentTimeMillis() + ".hprof.zip";
          MemoryDumpHelper.captureMemoryDumpZipped(path);
          message = "Dumped to " + path;
        }
        catch (Throwable t) {
          message = "Error: " + t.getMessage();
        }
        finally {
          final String _message = message;
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              myDumpMessageLabel.setText(_message);
              enableControls(true);
            }
          });
        }
      }
    };
    new Thread(task, "OOME Heap Dump").start();
  }

  @SuppressWarnings("Duplicates")
  private void enableControls(boolean enabled) {
    myHeapSizeField.setEnabled(enabled);
    myPermGenSizeField.setEnabled(enabled);
    myCodeCacheSizeField.setEnabled(enabled);
    myShutdownAction.setEnabled(enabled);
    myContinueAction.setEnabled(enabled);
    myHeapDumpAction.setEnabled(enabled);
  }

  @Override
  protected JComponent createCenterPanel() {
    return myContentPane;
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return myHeapDumpAction != null ? new Action[]{myShutdownAction, myContinueAction, myHeapDumpAction}
                                    : new Action[]{myShutdownAction, myContinueAction};
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myMemoryKind == MemoryKind.PERM_GEN ? myPermGenSizeField :
           myMemoryKind == MemoryKind.CODE_CACHE ? myCodeCacheSizeField :
           myHeapSizeField;
  }
}