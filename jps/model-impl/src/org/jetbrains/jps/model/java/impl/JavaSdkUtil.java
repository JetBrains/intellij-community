package org.jetbrains.jps.model.java.impl;

import com.intellij.openapi.util.SystemInfo;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public class JavaSdkUtil {
  public static List<File> getJdkClassesRoots(File file, boolean isJre) {
    FileFilter jarFileFilter = new FileFilter() {
      @Override
      @SuppressWarnings({"HardCodedStringLiteral"})
      public boolean accept(File f) {
        return !f.isDirectory() && f.getName().endsWith(".jar");
      }
    };

    File[] jarDirs;
    if (SystemInfo.isMac && !file.getName().startsWith("mockJDK")) {
      final File openJdkRtJar = new File(new File(new File(file, "jre"), "lib"), "rt.jar");
      if (openJdkRtJar.exists() && !openJdkRtJar.isDirectory()) {
        // OpenJDK
        File libFile = new File(file, "lib");
        File classesFile = openJdkRtJar.getParentFile();
        File libExtFile = new File(openJdkRtJar.getParentFile(), "ext");
        File libEndorsedFile = new File(libFile, "endorsed");
        jarDirs = new File[]{libEndorsedFile, libFile, classesFile, libExtFile};
      }
      else {
        File libFile = new File(file, "lib");
        File classesFile = new File(file, "../Classes");
        File libExtFile = new File(libFile, "ext");
        File libEndorsedFile = new File(libFile, "endorsed");
        jarDirs = new File[]{libEndorsedFile, libFile, classesFile, libExtFile};
      }
    }
    else {
      File jreLibFile = isJre ? new File(file, "lib") : new File(new File(file, "jre"), "lib");
      File jreLibExtFile = new File(jreLibFile, "ext");
      File jreLibEndorsedFile = new File(jreLibFile, "endorsed");
      jarDirs = new File[]{jreLibEndorsedFile, jreLibFile, jreLibExtFile};
    }

    Set<File> filter = new LinkedHashSet<File>();
    List<File> rootFiles = new ArrayList<File>();
    for (File jarDir : jarDirs) {
      if (jarDir != null && jarDir.isDirectory()) {
        File[] jarFiles = jarDir.listFiles(jarFileFilter);
        for (File jarFile : jarFiles) {
          final String jarFileName = jarFile.getName();
          if (jarFileName.equals("alt-rt.jar") || jarFileName.equals("alt-string.jar")) continue;
          try {
            // File.getCanonicalFile() allows us to filter out duplicate (symbolically linked) jar files,
            // commonly found in osx JDK distributions
            if (filter.add(jarFile.getCanonicalFile())) rootFiles.add(jarFile);
          }
          catch (IOException e) {
            // Symbolic links may fail to resolve. Just skip those jars as we won't be able to find virtual file in this case anyway.
          }
        }
      }
    }

    File classesZip = new File(new File(file, "lib"), "classes.zip");
    if (classesZip.isFile()) {
      rootFiles.add(classesZip);
    }

    File classesDir = new File(file, "classes");
    if (rootFiles.isEmpty() && classesDir.isDirectory()) {
      rootFiles.add(classesDir);
    }
    return rootFiles;
  }
}
