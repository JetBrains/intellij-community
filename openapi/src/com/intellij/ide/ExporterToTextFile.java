package com.intellij.ide;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.util.TooManyListenersException;

public interface ExporterToTextFile {
  JComponent getSettingsEditor();
  void addSettingsChangedListener(ChangeListener listener) throws TooManyListenersException;
  void removeSettingsChangedListener(ChangeListener listener);
  String getReportText();
  String getDefaultFilePath();
  void exportedTo(String filePath);
  boolean canExport();
}
