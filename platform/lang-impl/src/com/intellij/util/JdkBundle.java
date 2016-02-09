/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.regex.Pattern;

public class JdkBundle {
  @NotNull private static final Logger LOG = Logger.getInstance("#com.intellij.util.JdkBundle");

  private static final Pattern[] VERSION_UPDATE_PATTERNS = {
    Pattern.compile("^java version \"([\\d]+\\.[\\d]+\\.[\\d]+)_([\\d]+)\".*", Pattern.MULTILINE),
    Pattern.compile("^openjdk version \"([\\d]+\\.[\\d]+\\.[\\d]+)_([\\d]+).*\".*", Pattern.MULTILINE),
    Pattern.compile("^[a-zA-Z() \"\\d]*([\\d]+\\.[\\d]+\\.?[\\d]*).*", Pattern.MULTILINE)
  };

  @NotNull private File myBundleAsFile;
  @NotNull private String myBundleName;
  @Nullable private Pair<Version, Integer> myVersionUpdate;
  private boolean myBoot;
  private boolean myBundled;

  JdkBundle(@NotNull File bundleAsFile,
            @NotNull String bundleName,
            @Nullable Pair<Version, Integer> versionUpdate, boolean boot, boolean bundled) {
    myBundleAsFile = bundleAsFile;
    myBundleName = bundleName;
    myVersionUpdate = versionUpdate;
    myBoot = boot;
    myBundled = bundled;
  }


  @Nullable
  public static JdkBundle createBundle(@NotNull File jvm, boolean boot, boolean bundled) {
    String homeSubPath = SystemInfo.isMac ? "Contents/Home" : "";
    return createBundle(jvm, homeSubPath, boot, bundled);
  }

  @Nullable
  static JdkBundle createBundle(@NotNull File jvm, @NotNull String homeSubPath, boolean boot, boolean bundled) {
    File javaHome = SystemInfo.isMac ? new File(jvm, homeSubPath) : jvm;
    if (bundled) javaHome = new File(PathManager.getHomePath(), javaHome.getPath());

    boolean isValidBundle = true;

    String jreCheck = System.getProperty("idea.jre.check");
    if (jreCheck != null && "true".equals(jreCheck)) {
      isValidBundle = new File(javaHome, "lib" + File.separator + "tools.jar").exists();
    }

    if (!SystemInfo.isMac && !isValidBundle) return null; // Skip jre

    File absJvmLocation = bundled ? new File(PathManager.getHomePath(), jvm.getPath()) : jvm;
    Pair<String, Pair<Version, Integer>> nameVersionAndUpdate = getJDKNameVersionAndUpdate(absJvmLocation, homeSubPath);

    if (SystemInfo.isMac && nameVersionAndUpdate.second != null && nameVersionAndUpdate.second.first.isOrGreaterThan(1, 7) &&
        !isValidBundle) {
      return null; // Skip jre
    }

    return new JdkBundle(jvm, nameVersionAndUpdate.first, nameVersionAndUpdate.second, boot, bundled);
  }

  @Nullable
  public static JdkBundle createBoot() {
    return createBoot(true);
  }

  @Nullable
  static JdkBundle createBoot(boolean adjustToMacBundle) {
    File bootJDK = new File(System.getProperty("java.home")).getParentFile();
    if (SystemInfo.isMac && adjustToMacBundle) {
      bootJDK = bootJDK.getParentFile().getParentFile();
      return createBundle(bootJDK, true, false);
    }
    return createBundle(bootJDK, "", true, false);
  }

  @NotNull
  File getAbsoluteLocation() {
    return myBundled ? new File(PathManager.getHomePath(), myBundleAsFile.getPath()) : myBundleAsFile;
  }

  @NotNull
  public File getLocation() {
    return myBundleAsFile;
  }

  public String getVisualRepresentation() {
    StringBuilder representation = new StringBuilder(myBundleName);
    if (myVersionUpdate != null) {
      representation.append(myVersionUpdate.first.toString()).append((myVersionUpdate.second > 0 ? "_" + myVersionUpdate.second : ""));
    }

    if (myBoot || myBundled) {
      representation.append(" [");
      if (myBoot) representation.append(myBundled ? "boot, " : "boot");
      if (myBundled) representation.append("bundled");
      representation.append("]");
    }
    return representation.toString();
  }

  public void setBundled(boolean bundled) {
    myBundled = bundled;
  }

  public boolean isBoot() {
    return myBoot;
  }

  @NotNull
  public String getBundleName() {
    return myBundleName;
  }

  @Nullable
  Pair<Version, Integer> getVersionUpdate() {
    return myVersionUpdate;
  }

  @Nullable
  public Version getVersion() {
    return myVersionUpdate != null ? myVersionUpdate.first : null;
  }

  @NotNull
  String getNameVersion() {
    return myBundleName + ((myVersionUpdate != null) ? myVersionUpdate.first.toString() : "");
  }

  private static Pair<String, Pair<Version, Integer>> getJDKNameVersionAndUpdate(File jvm, String homeSubPath) {
    GeneralCommandLine commandLine = new GeneralCommandLine().withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.NONE);
    commandLine.setExePath(new File(jvm,  homeSubPath + File.separator +  "jre" +
                           File.separator + "bin" + File.separator + "java").getAbsolutePath());
    commandLine.addParameter("-version");

    String displayVersion = null;
    Pair<Version, Integer> versionAndUpdate = null;
    try {
      displayVersion = ExecUtil.readFirstLine(commandLine.createProcess().getErrorStream(), null);
    }
    catch (ExecutionException e) {
      // Checking for jdk 6 on mac
      if (SystemInfo.isMac) {
        commandLine.setExePath(new File(jvm,  homeSubPath + File.separator +  "bin" + File.separator + "java").getAbsolutePath());
        try {
          displayVersion = ExecUtil.readFirstLine(commandLine.createProcess().getErrorStream(), null);
        }
        catch (ExecutionException e1) {
          LOG.debug(e);
        }
      }
      LOG.debug(e);
    }

    if (displayVersion != null) {
      versionAndUpdate = VersionUtil.parseVersionAndUpdate(displayVersion, VERSION_UPDATE_PATTERNS);
      displayVersion = displayVersion.replaceFirst("\".*\"", "");
    }
    else {
      displayVersion = jvm.getName();
    }

    return Pair.create(displayVersion, versionAndUpdate);
  }

  public boolean isBundled() {
    return myBundled;
  }

  public void setBoot(boolean boot) {
    myBoot = boot;
  }
}
