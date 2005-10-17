/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jun 7, 2002
 * Time: 8:27:57 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Vector;

public class ClassFinder {
  private final Vector classNameList = new Vector();
  private final int startPackageName;

  public ClassFinder(final File classPathRoot, final String packageRoot) throws IOException {
    startPackageName = classPathRoot.getAbsolutePath().length() + 1;
    String directoryOffset = packageRoot.replace('.', File.separatorChar);
    findAndStoreTestClasses(new File(classPathRoot, directoryOffset));
  }

  private String computeClassName(final File file) {
    String absPath = file.getAbsolutePath();
    String packageBase = absPath.substring(startPackageName, absPath.length() - 6);
    return packageBase.replace(File.separatorChar, '.');
  }

  private void findAndStoreTestClasses(final File currentDirectory) throws IOException {
    String[] files = currentDirectory.list();
    if (files == null) return;
    for (int i = 0; i < files.length; i++) {
      File file = new File(currentDirectory, files[i]);
      String fileName = file.getName();
      String suffix = "Test.class";
      int idx = fileName.indexOf(suffix);

      if (idx != -1 && (fileName.length() - idx) == suffix.length()) {
        String className = computeClassName(file);
        classNameList.add(className);
      } else {
        if (file.isDirectory()) {
          findAndStoreTestClasses(file);
        }
      }
    }
  }

  public Iterator getClasses() {
    return classNameList.iterator();
  }
}
