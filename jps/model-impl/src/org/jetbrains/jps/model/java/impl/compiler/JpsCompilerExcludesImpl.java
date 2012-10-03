package org.jetbrains.jps.model.java.impl.compiler;

import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.jps.util.JpsPathUtil;
import org.jetbrains.jps.model.java.compiler.JpsCompilerExcludes;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * @author nik
 */
public class JpsCompilerExcludesImpl implements JpsCompilerExcludes {
  private final Set<File> myFiles = new HashSet<File>();
  private final Set<File> myDirectories = new HashSet<File>();
  private final Set<File> myRecursivelyExcludedDirectories = new HashSet<File>();

  @Override
  public void addExcludedFile(String url) {
    addExcludedFile(JpsPathUtil.urlToFile(url));
  }

  @Override
  public void addExcludedDirectory(String url, boolean recursively) {
    addExcludedDirectory(JpsPathUtil.urlToFile(url), recursively);
  }

  public void addExcludedFile(File file) {
    myFiles.add(file);
  }

  public void addExcludedDirectory(File dir, boolean recursively) {
    (recursively ? myRecursivelyExcludedDirectories : myDirectories).add(dir);
  }

  @Override
  public  boolean isExcluded(File file) {
    if (myFiles.contains(file)) {
      return true;
    }

    if (!myDirectories.isEmpty() || !myRecursivelyExcludedDirectories.isEmpty()) { // optimization
      File parent = FileUtilRt.getParentFile(file);
      if (myDirectories.contains(parent)) {
        return true;
      }

      while (parent != null) {
        if (myRecursivelyExcludedDirectories.contains(parent)) {
          return true;
        }
        parent = FileUtilRt.getParentFile(parent);
      }
    }
    return false;
  }
}
