package org.jetbrains.jps.builders;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.jps.builders.impl.logging.ProjectBuilderLoggerBase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public class TestProjectBuilderLogger extends ProjectBuilderLoggerBase {
  private MultiMap<String, File> myCompiledFiles = new MultiMap<String, File>();
  
  @Override
  public void logDeletedFiles(Collection<String> outputs) {
    super.logDeletedFiles(outputs);
  }

  @Override
  public void logCompiledFiles(Collection<File> files, String builderName, String description) throws IOException {
    myCompiledFiles.putValues(builderName, files);
  }

  public void clear() {
    myCompiledFiles.clear();
  }
  
  public void assertCompiled(String builderName, File baseDir, String... paths) {
    Collection<File> compiled = myCompiledFiles.get(builderName);
    List<String> relativePaths = new ArrayList<String>();
    for (File file : compiled) {
      String path;
      if (FileUtil.isAncestor(baseDir, file, false)) {
        path = FileUtil.getRelativePath(baseDir, file);
      }
      else {
        path = file.getAbsolutePath();
      }
      relativePaths.add(FileUtil.toSystemIndependentName(path));
    }
    UsefulTestCase.assertSameElements(relativePaths, paths);
  }
  
  @Override
  protected void logLine(String message) {
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
