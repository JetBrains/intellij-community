package com.intellij.conversion;

import java.io.File;
import java.util.List;

/**
* @author nik
*/
public interface ConversionListener {
  void conversionNeeded();
  void successfullyConverted(File backupDir);
  void error(String message);
  void cannotWriteToFiles(final List<File> readonlyFiles);
}
