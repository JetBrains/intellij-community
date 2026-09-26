// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.containers.FileCollectionFactory;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.impl.logging.ProjectBuilderLoggerBase;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class TestProjectBuilderLogger extends ProjectBuilderLoggerBase {
  private final MultiMap<String, File> compiledFiles = new MultiMap<>();
  private final Set<File> deletedFiles = FileCollectionFactory.createCanonicalFileSet();
  private final List<String> logLines = new ArrayList<>();

  @Override
  public void logDeletedFiles(Collection<String> paths) {
    super.logDeletedFiles(paths);
    for (String path : paths) {
      deletedFiles.add(new File(path));
    }
  }

  @Override
  public void logCompiledFiles(Collection<File> files, String builderId, String description) throws IOException {
    super.logCompiledFiles(files, builderId, description);
    compiledFiles.putValues(builderId, files);
  }

  @Override
  public void logCompiledPaths(@NotNull Collection<String> paths, String builderId, String description) {
    super.logCompiledPaths(paths, builderId, description);
    //noinspection SSBasedInspection
    compiledFiles.putValues(builderId, paths.stream().map(File::new).toList());
  }

  @Override
  public void logCompiled(@NotNull Collection<Path> files, String builderId, String description) {
    //noinspection SSBasedInspection
    compiledFiles.putValues(builderId, files.stream().map(Path::toFile).toList());
  }

  public void clearFilesData() {
    compiledFiles.clear();
    deletedFiles.clear();
  }

  public void clearLog() {
    logLines.clear();
  }

  public void assertCompiled(String builderName, File[] baseDirs, String... paths) {
    assertRelativePaths(baseDirs, compiledFiles.get(builderName), paths);
  }

  public void assertDeleted(File[] baseDirs, String... paths) {
    assertRelativePaths(baseDirs, deletedFiles, paths);
  }

  private static void assertRelativePaths(File[] baseDirs, Collection<File> files, String[] expected) {
    List<String> relativePaths = new ArrayList<>();
    for (File file : files) {
      String path = file.getAbsolutePath();
      for (File baseDir : baseDirs) {
        if (baseDir != null && FileUtil.isAncestor(baseDir, file, false)) {
          path = FileUtil.getRelativePath(baseDir, file);
          break;
        }
      }
      relativePaths.add(FileUtil.toSystemIndependentName(path));
    }
    UsefulTestCase.assertSameElements(relativePaths, expected);
  }

  @Override
  protected void logLine(String message) {
    logLines.add(message);
  }

  public String getFullLog(File... baseDirs) {
    return StringUtil.join(logLines, s -> {
      for (File dir : baseDirs) {
        if (dir != null) {
          String path = FileUtil.toSystemIndependentName(dir.getAbsolutePath()) + "/";
          if (s.startsWith(path)) {
            return s.substring(path.length());
          }
        }
      }
      return s;
    }, "\n");
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
