// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SystemProperties;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class JavaHomeFinder {

  /**
   * Tries to find existing Java SDKs on this computer.
   * If no JDK found, returns possible folders to start file chooser.
   * @return suggested sdk home paths (sorted)
   */
  @NotNull
  public static List<String> suggestHomePaths() {
    JavaHomeFinder javaFinder = getFinder();
    Collection<String> foundPaths = javaFinder.findExistingJdks();
    ArrayList<String> paths = new ArrayList<>(foundPaths);
    paths.sort((o1, o2) -> Comparing.compare(JavaVersion.tryParse(o2), JavaVersion.tryParse(o1)));
    return paths;
  }

  @NotNull
  protected abstract List<String> findExistingJdks();

  private static JavaHomeFinder getFinder() {
    if (!Registry.is("java.detector.enabled")) {
      return new JavaHomeFinder() {
        @NotNull
        @Override
        protected List<String> findExistingJdks() {
          return Collections.emptyList();
        }
      };
    }

    if (SystemInfo.isWindows) {
      return new WindowsJavaFinder();
    }
    if (SystemInfo.isMac) {
      return new MacFinder();
    }
    if (SystemInfo.isLinux) {
      return new DefaultFinder("/usr/java", "/opt/java", "/usr/lib/jvm");
    }
    if (SystemInfo.isSolaris) {
      return new DefaultFinder("/usr/jdk");
    }
    return new DefaultFinder();
  }

  protected void scanFolder(File folder, List<? super String> result) {
    if (JdkUtil.checkForJdk(folder))
      result.add(folder.getAbsolutePath());

    for (File file : ObjectUtils.notNull(folder.listFiles(), ArrayUtilRt.EMPTY_FILE_ARRAY)) {
      file = adjustPath(file);
      if (JdkUtil.checkForJdk(file)) {
        result.add(file.getAbsolutePath());
      }
    }
  }

  protected File adjustPath(File file) {
    return file;
  }

  protected static File getJavaHome() {
    String property = SystemProperties.getJavaHome();
    if (property == null)
      return null;

    File javaHome = new File(property).getParentFile();//actually java.home points to to jre home
    return javaHome == null || !javaHome.isDirectory() ? null : javaHome;
  }

  protected static class DefaultFinder extends JavaHomeFinder {

    private final String[] myPaths;

    protected DefaultFinder(String... paths) {
      File javaHome = getJavaHome();
      myPaths = javaHome == null ? paths : ArrayUtil.prepend(javaHome.getAbsolutePath(), paths);
    }

    @NotNull
    @Override
    public List<String> findExistingJdks() {
      List<String> result = new ArrayList<>();
      for (String path : myPaths) {
        scanFolder(new File(path), result);
      }
      for (File dir : guessByPathVariable()) {
        scanFolder(dir, result);
      }
      return result;
    }

    public Collection<File> guessByPathVariable() {
      String pathVarString = System.getenv("PATH");
      if (pathVarString == null || pathVarString.isEmpty()) return Collections.emptyList();
      ArrayList<File> dirsToCheck = new ArrayList<>(1);
      String[] pathEntries = pathVarString.split(File.pathSeparator);
      for (String p : pathEntries) {
        File dir = new File(p);
        if (StringUtilRt.equal(dir.getName(), "bin", !SystemInfo.isWindows)) {
          File f1 = new File(p, "java");
          File f2 = new File(p, "javac");
          if (f1.isFile() && f2.isFile()) {
            File f1c = canonize(f1);
            File f2c = canonize(f2);
            File d1 = granny(f1c);
            File d2 = granny(f2c);
            if (d1 != null && d2 != null && FileUtil.filesEqual(d1, d2)) {
              dirsToCheck.add(d1);
            }
          }
        }
      }
      return dirsToCheck;
    }
  }

  private static class MacFinder extends DefaultFinder {

    MacFinder() {
      super("/Library/Java/JavaVirtualMachines", "/System/Library/Java/JavaVirtualMachines");
    }

    @NotNull
    @Override
    public List<String> findExistingJdks() {
      List<String> list = super.findExistingJdks();
      if (new File("/usr/libexec/java_home").canExecute()) {
        String path = ExecUtil.execAndReadLine(new GeneralCommandLine("/usr/libexec/java_home"));
        if (path != null && new File(path).isDirectory()) {
          list.add(path);
        }
      }
      return list;
    }

    @Override
    protected File adjustPath(File file) {
      File home = new File(file, "/Home");
      if (home.exists()) return home;

      home = new File(file, "Contents/Home");
      if (home.exists()) return home;

      return file;
    }
  }

  @NotNull
  private static File canonize(@NotNull File file) {
    try {
      return file.getCanonicalFile();
    }
    catch (IOException ioe) {
      return file.getAbsoluteFile();
    }
  }

  @Nullable
  private static File granny(@Nullable File file) {
    File parent = file.getParentFile();
    return parent != null ? parent.getParentFile() : null;
  }

}
