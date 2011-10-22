package org.jetbrains.jps.incremental;

import org.jetbrains.jps.Module;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/21/11
 */
public class FilesCollector implements FileProcessor{
  public static FileFilter ALL_FILES = new FileFilter() {
    public boolean accept(File pathname) {
      return true;
    }
  };
  private final Collection<File> myContainer;
  private final FileFilter myFilter;

  public FilesCollector() {
    this(ALL_FILES);
  }

  public FilesCollector(FileFilter filter) {
    this(new ArrayList<File>(), filter);
  }

  public FilesCollector(Collection<File> container, FileFilter filter) {
    myFilter = filter;
    myContainer = container;
  }

  public Collection<File> getFiles() {
    return myContainer;
  }

  public boolean apply(Module module, File file, String sourceRoot) throws Exception {
    if (myFilter.accept(file)) {
      myContainer.add(file);
    }
    return true;
  }
}
