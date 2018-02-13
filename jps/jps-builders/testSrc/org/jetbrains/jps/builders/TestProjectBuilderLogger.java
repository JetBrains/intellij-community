/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.builders;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import org.jetbrains.jps.builders.impl.logging.ProjectBuilderLoggerBase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public class TestProjectBuilderLogger extends ProjectBuilderLoggerBase {
  private final MultiMap<String, File> myCompiledFiles = new MultiMap<>();
  private final Set<File> myDeletedFiles = new THashSet<>(FileUtil.FILE_HASHING_STRATEGY);
  private final List<String> myLogLines = new ArrayList<>();
  
  @Override
  public void logDeletedFiles(Collection<String> paths) {
    super.logDeletedFiles(paths);
    for (String path : paths) {
      myDeletedFiles.add(new File(path));
    }
  }

  @Override
  public void logCompiledFiles(Collection<File> files, String builderName, String description) throws IOException {
    super.logCompiledFiles(files, builderName, description);
    myCompiledFiles.putValues(builderName, files);
  }

  public void clearFilesData() {
    myCompiledFiles.clear();
    myDeletedFiles.clear();
  }

  public void clearLog() {
    myLogLines.clear();
  }

  public void assertCompiled(String builderName, File[] baseDirs, String... paths) {
    assertRelativePaths(baseDirs, myCompiledFiles.get(builderName), paths);
  }

  public void assertDeleted(File[] baseDirs, String... paths) {
    assertRelativePaths(baseDirs, myDeletedFiles, paths);
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
    myLogLines.add(message);
  }

  public String getFullLog(final File... baseDirs) {
    return StringUtil.join(myLogLines, s -> {
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
