/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.conversion.impl;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * @author nik
 */
public class ProjectConversionUtil {
  @NonNls private static final String PROJECT_FILES_BACKUP = "projectFilesBackup";
  @NonNls private static final String BACKUP_EXTENSION = "backup";

  private ProjectConversionUtil() {
  }

  public static File backupFile(File file) throws IOException {
    final String fileName = FileUtil.createSequentFileName(file.getParentFile(), file.getName(), BACKUP_EXTENSION);
    final File backup = new File(file.getParentFile(), fileName);
    FileUtil.copy(file, backup);
    return backup; 
  }

  public static File backupFiles(final Collection<File> files, final File parentDir) throws IOException {
    final String dirName = FileUtil.createSequentFileName(parentDir, PROJECT_FILES_BACKUP, "");
    File backupDir = new File(parentDir, dirName);
    backupDir.mkdirs();
    for (File file : files) {
      FileUtil.copy(file, new File(backupDir, file.getName()));
    }
    return backupDir;
  }

}
