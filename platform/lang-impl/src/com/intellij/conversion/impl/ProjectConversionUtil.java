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
    File backupDir = getBackupDir(parentDir);
    backupFiles(files, parentDir, backupDir);
    return backupDir;
  }

  public static void backupFiles(Collection<File> files, File parentDir, File backupDir) throws IOException {
    backupDir.mkdirs();
    for (File file : files) {
      final File target;
      if (FileUtil.isAncestor(parentDir, file, true)) {
        final String relativePath = FileUtil.getRelativePath(parentDir, file);
        target = new File(backupDir.getAbsolutePath() + File.separator + relativePath);
        FileUtil.createParentDirs(target);
      }
      else {
        target = new File(backupDir, file.getName());
      }
      FileUtil.copy(file, target);
    }
  }

  public static File getBackupDir(File parentDir) {
    final String dirName = FileUtil.createSequentFileName(parentDir, PROJECT_FILES_BACKUP, "");
    return new File(parentDir, dirName);
  }

}
