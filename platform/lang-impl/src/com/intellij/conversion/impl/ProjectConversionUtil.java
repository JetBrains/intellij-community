// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.conversion.impl;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;

public final class ProjectConversionUtil {
  public static final @NonNls String PROJECT_FILES_BACKUP = "projectFilesBackup";
  private static final @NonNls String BACKUP_EXTENSION = "backup";

  private ProjectConversionUtil() {
  }

  public static File backupFile(@NotNull Path file) throws IOException {
    final String fileName = FileUtil.createSequentFileName(file.getParent().toFile(), file.getFileName().toString(), BACKUP_EXTENSION);
    final File backup = file.getParent().resolve(fileName).toFile();
    FileUtil.copy(file.toFile(), backup);
    return backup;
  }

  public static @NotNull Path backupFiles(@NotNull Collection<? extends Path> files, @NotNull Path parentDir) throws IOException {
    Path backupDir = getBackupDir(parentDir);
    backupFiles(files, parentDir, backupDir);
    return backupDir;
  }

  public static void backupFiles(@NotNull Collection<? extends Path> files, @NotNull Path parentDir, @NotNull Path backupDir) throws IOException {
    Files.createDirectories(backupDir);
    for (Path sourceFile : files) {
      Path targetFile;
      if (sourceFile.startsWith(parentDir)) {
        targetFile = backupDir.resolve(parentDir.relativize(sourceFile));

        Path parent = targetFile.getParent();
        if (parent != backupDir) {
          Files.createDirectories(parent);
        }
      }
      else {
        targetFile = backupDir.resolve(sourceFile.getFileName());
      }

      // any file here expected to be regular file and not a directory
      try {
        Files.copy(sourceFile, targetFile, StandardCopyOption.COPY_ATTRIBUTES);
      }
      catch (NoSuchFileException ignore) {
        // if source file doesn't exist - it's ok
      }
    }
  }

  public static @NotNull Path getBackupDir(@NotNull Path parentDir) {
    String dirName = FileUtil.createSequentFileName(parentDir.toFile(), PROJECT_FILES_BACKUP, "");
    return parentDir.resolve(dirName);
  }
}
