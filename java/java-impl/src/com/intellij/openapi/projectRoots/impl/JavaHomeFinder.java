/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.projectRoots.impl;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public abstract class JavaHomeFinder {

  /**
   * Tries to find existing Java SDKs on this computer.
   * If no JDK found, returns possible folders to start file chooser.
   * @return suggested sdk home paths (sorted)
   */
  @NotNull
  public static List<String> suggestHomePaths() {
    List<String> paths = new ArrayList<>(new HashSet<>(getFinder().findExistingJdks()));
    paths.sort((o1, o2) -> Comparing.compare(JavaSdkVersion.fromVersionString(o2), JavaSdkVersion.fromVersionString(o1)));
    return paths;
  }

  @NotNull
  protected abstract List<String> findExistingJdks();

  private static JavaHomeFinder getFinder() {
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

  protected static void scanFolder(File folder, List<String> result) {
    if (JdkUtil.checkForJdk(folder))
      result.add(folder.getAbsolutePath());

    for (File file : ObjectUtils.notNull(folder.listFiles(), ArrayUtil.EMPTY_FILE_ARRAY)) {
      if (JdkUtil.checkForJdk(file)) {
        result.add(file.getAbsolutePath());
      }
    }
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
        JavaHomeFinder.scanFolder(new File(path), result);
      }
      return result;
    }
  }

  private static class MacFinder extends DefaultFinder {

    public MacFinder() {
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
  }
}
