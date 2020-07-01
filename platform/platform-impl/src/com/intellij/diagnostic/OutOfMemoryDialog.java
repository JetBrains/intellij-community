// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.diagnostic.VMOptions.MemoryKind;
import com.intellij.diagnostic.hprof.action.HeapDumpSnapshotRunnable;
import com.intellij.diagnostic.report.MemoryReportReason;
import com.intellij.ide.IdeBundle;
import com.intellij.idea.Main;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.MemoryDumpHelper;
import com.intellij.util.SystemProperties;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.Path;

public class OutOfMemoryDialog extends DialogWrapper {
  private final MemoryKind myMemoryKind;

  private JPanel myContentPane;
  private JBLabel myIconLabel;
  private JBLabel myMessageLabel;
  private JBLabel myHeapSizeLabel;
  private JTextField myHeapSizeField;
  private JBLabel myHeapUnitsLabel;
  private JBLabel myHeapCurrentValueLabel;
  private JBLabel myMetaspaceSizeLabel;
  private JTextField myMetaspaceSizeField;
  private JBLabel myMetaspaceUnitsLabel;
  private JBLabel myMetaspaceCurrentValueLabel;
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
    myMessageLabel.setBorder(JBUI.Borders.emptyBottom(10));

    Path file = VMOptions.getWriteFile();
    if (file != null) {
      mySettingsFileHintLabel.setText(DiagnosticBundle.message("diagnostic.out.of.memory.willBeSavedTo", file.toString()));
      mySettingsFileHintLabel.setBorder(JBUI.Borders.emptyTop(10));
    }
    else {
      mySettingsFileHintLabel.setVisible(false);
      myHeapSizeField.setEnabled(false);
      myMetaspaceSizeField.setEnabled(false);
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
        new HeapDumpSnapshotRunnable(MemoryReportReason.UserInvoked, HeapDumpSnapshotRunnable.AnalysisOption.SCHEDULE_ON_NEXT_START).run();
      }
    };

    configControls(MemoryKind.HEAP, myHeapSizeLabel, myHeapSizeField, myHeapUnitsLabel, myHeapCurrentValueLabel);
    configControls(MemoryKind.METASPACE, myMetaspaceSizeLabel, myMetaspaceSizeField, myMetaspaceUnitsLabel, myMetaspaceCurrentValueLabel);
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
      VMOptions.writeOption(MemoryKind.HEAP, heapSize);
    }
    catch (NumberFormatException ignored) { }

    try {
      int codeCacheSize = Integer.parseInt(myMetaspaceSizeField.getText());
      VMOptions.writeOption(MemoryKind.METASPACE, codeCacheSize);
    }
    catch (NumberFormatException ignored) { }

    try {
      int codeCacheSize = Integer.parseInt(myCodeCacheSizeField.getText());
      VMOptions.writeOption(MemoryKind.CODE_CACHE, codeCacheSize);
    }
    catch (NumberFormatException ignored) { }
  }

  @SuppressWarnings("SSBasedInspection")
  private void snapshot() {
    enableControls(false);
    myDumpMessageLabel.setVisible(true);
    myDumpMessageLabel.setText(DiagnosticBundle.message("label.dumping.memory"));

    Runnable task = () -> {
      TimeoutUtil.sleep(250);  // to give UI chance to update
      String message = "";
      try {
        String name = StringUtil.toLowerCase(ApplicationNamesInfo.getInstance().getFullProductName().replace(' ', '-'));
        String path = SystemProperties.getUserHome() + File.separator + "heapDump-" + name + '-' + System.currentTimeMillis() + ".hprof.zip";
        MemoryDumpHelper.captureMemoryDumpZipped(path);
        message = "Dumped to " + path;
      }
      catch (Throwable t) {
        message = "Error: " + t.getMessage();
      }
      finally {
        final String _message = message;
        SwingUtilities.invokeLater(() -> {
          myDumpMessageLabel.setText(_message);
          enableControls(true);
        });
      }
    };
    new Thread(task, "OOME Heap Dump").start();
  }

  private void enableControls(boolean enabled) {
    myHeapSizeField.setEnabled(enabled);
    myMetaspaceSizeField.setEnabled(enabled);
    myCodeCacheSizeField.setEnabled(enabled);
    myShutdownAction.setEnabled(enabled);
    myContinueAction.setEnabled(enabled);
    myHeapDumpAction.setEnabled(enabled);
  }

  @Override
  protected JComponent createCenterPanel() {
    return myContentPane;
  }

  @Override
  protected Action @NotNull [] createActions() {
    return myHeapDumpAction != null ? new Action[]{myShutdownAction, myContinueAction, myHeapDumpAction}
                                    : new Action[]{myShutdownAction, myContinueAction};
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myMemoryKind == MemoryKind.METASPACE ? myMetaspaceSizeField :
           myMemoryKind == MemoryKind.CODE_CACHE ? myCodeCacheSizeField :
           myHeapSizeField;
  }
}