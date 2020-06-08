// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.JdkBundle;
import com.intellij.util.JdkBundleList;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.stream.Stream;

/**
 * @author denis
 */
public final class JdkUtils {

  private static final JavaVersion MIN_VERSION = JavaVersion.compose(8), MAX_VERSION = null;


  private static final String WINDOWS_X64_JVM_LOCATION = "Program Files/Java";
  private static final String WINDOWS_X86_JVM_LOCATION = "Program Files (x86)/Java";
  private static final String[] MAC_OS_JVM_LOCATIONS = {"/Library/Java/JavaVirtualMachines"};
  private static final String[] LINUX_JVM_LOCATIONS = {"/usr/lib/jvm", "/usr/java"};

  private static final String CONFIG_FILE_EXT =
    (!SystemInfo.isWindows ? ".jdk" : SystemInfo.is64Bit ? "64.exe.jdk" : ".exe.jdk");

  public static JdkBundleList findJdkBundles(@Nullable ProgressIndicator indicator) {
    JdkBundleList bundleList = new JdkBundleList();

    bundleList.addBundle(JdkBundle.createBoot());

    JdkBundle bundledJdk = JdkBundle.createBundled();
    if (bundledJdk != null && bundledJdk.isOperational()) {
      bundleList.addBundle(bundledJdk);
    }

    String[] locations = ArrayUtilRt.EMPTY_STRING_ARRAY;
    if (SystemInfo.isWindows) {
      String dir = SystemInfo.is32Bit ? WINDOWS_X86_JVM_LOCATION : WINDOWS_X64_JVM_LOCATION;
      locations = Stream.of(File.listRoots()).map(root -> new File(root, dir).getPath()).toArray(String[]::new);
    }
    else if (SystemInfo.isMac) {
      locations = MAC_OS_JVM_LOCATIONS;
    }
    else if (SystemInfo.isLinux) {
      locations = LINUX_JVM_LOCATIONS;
    }
    for (String location : locations) {
      if (indicator != null) indicator.checkCanceled();
      bundleList.addBundlesFromLocation(location, MIN_VERSION, MAX_VERSION);
    }

    return bundleList;
  }
}