/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.util;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * @author max
 */
public class BuildNumber implements Comparable<BuildNumber> {
  private static final String BUILD_NUMBER = "__BUILD_NUMBER__";
  private static final String STAR = "*";
  private static final String SNAPSHOT = "SNAPSHOT";
  private static final String FALLBACK_VERSION = "999.SNAPSHOT";

  public static final int SNAPSHOT_VALUE = Integer.MAX_VALUE;

  private static class Holder {
    private static final BuildNumber CURRENT_VERSION = fromFile();
  }

  @NotNull private final String myProductCode;
  @NotNull private final int[] myComponents;
  
  public BuildNumber(@NotNull String productCode, int baselineVersion, int buildNumber) {
    this(productCode, new int[]{baselineVersion, buildNumber});
  }

  public BuildNumber(@NotNull String productCode, @NotNull int... components) {
    myProductCode = productCode;
    myComponents = components;
  }

  public String asString() {
    return asString(true, true);
  }

  public String asStringWithoutProductCode() {
    return asString(false, true);
  }

  public String asStringWithoutProductCodeAndSnapshot() {
    return asString(false, false);
  }

  private String asString(boolean includeProductCode, boolean withSnapshotMarker) {
    StringBuilder builder = new StringBuilder();

    if (includeProductCode && !StringUtil.isEmpty(myProductCode)) {
      builder.append(myProductCode).append('-');
    }

    for (int each : myComponents) {
      if (each != SNAPSHOT_VALUE) {
        builder.append(each);
      }
      else if (withSnapshotMarker) {
        builder.append(SNAPSHOT);
      }
      builder.append('.');
    }
    if (builder.charAt(builder.length() - 1) == '.') builder.setLength(builder.length() - 1);

    return builder.toString();
  }

  public static BuildNumber fromString(String version) {
    return fromString(version, null, null);
  }

  public static BuildNumber fromStringWithProductCode(String version, String productCode) {
    return fromString(version, null, productCode);
  }

  public static BuildNumber fromString(String version, @Nullable String pluginName, @Nullable String productCodeIfAbsentInVersion) {
    if (StringUtil.isEmptyOrSpaces(version)) return null;

    if (BUILD_NUMBER.equals(version) || SNAPSHOT.equals(version)) {
      return new BuildNumber(productCodeIfAbsentInVersion != null ? productCodeIfAbsentInVersion : "", currentVersion().myComponents);
    }

    String code = version;
    int productSeparator = code.indexOf('-');
    String productCode;
    if (productSeparator > 0) {
      productCode = code.substring(0, productSeparator);
      code = code.substring(productSeparator + 1);
    }
    else {
      productCode = "";
    }
    productCode = productCode == "" && productCodeIfAbsentInVersion != null ? productCodeIfAbsentInVersion : productCode;

    int baselineVersionSeparator = code.indexOf('.');
    int baselineVersion;
    int buildNumber;

    if (baselineVersionSeparator > 0) {
      String baselineVersionString = code.substring(0, baselineVersionSeparator);
      if (baselineVersionString.trim().isEmpty()) return null;

      List<String> stringComponents = StringUtil.split(code, ".");
      TIntArrayList intComponentsList = new TIntArrayList();

      for (String stringComponent : stringComponents) {
        int comp = parseBuildNumber(version, stringComponent, pluginName);
        intComponentsList.add(comp);
        if (comp == SNAPSHOT_VALUE) break;
      }

      int[] intComponents = intComponentsList.toNativeArray();

      return new BuildNumber(productCode, intComponents);
    }
    else {
      buildNumber = parseBuildNumber(version, code, pluginName);

      if (buildNumber <= 2000) {
        // it's probably a baseline, not a build number
        return new BuildNumber(productCode, buildNumber, 0);
      }

      baselineVersion = getBaseLineForHistoricBuilds(buildNumber);
      return new BuildNumber(productCode, baselineVersion, buildNumber);
    }
  }

  private static int parseBuildNumber(String version, String code, String pluginName) {
    if (SNAPSHOT.equals(code) || BUILD_NUMBER.equals(code)) {
      return SNAPSHOT_VALUE;
    }
    if (STAR.equals(code)) {
      return SNAPSHOT_VALUE;
    }
    
    try {
      return Integer.parseInt(code);
    }
    catch (NumberFormatException e) {
      throw new RuntimeException("Invalid version number: " + version + "; plugin name: " + pluginName);
    }
  }

  private static BuildNumber fromFile() {
    try {
      String home = PathManager.getHomePath();
      File buildTxtFile = FileUtil.findFirstThatExist(home + "/build.txt", home + "/Resources/build.txt", home + "/community/build.txt", home + "/ultimate/community/build.txt");
      if (buildTxtFile != null) {
        String text = FileUtil.loadFile(buildTxtFile).trim();
        return fromString(text);
      }
    }
    catch (IOException ignored) { }

    return fromString(FALLBACK_VERSION);
  }

  /**
   * This method is for internal platform use only. In regular code use {@link com.intellij.openapi.application.ApplicationInfo#getBuild()} instead.
   */
  public static BuildNumber currentVersion() {
    return Holder.CURRENT_VERSION;
  }

  @Override
  public String toString() {
    return asString();
  }

  @Override
  public int compareTo(@NotNull BuildNumber o) {
    int[] c1 = myComponents;
    int[] c2 = o.myComponents;
    
    for (int i = 0; i < Math.min(c1.length, c2.length); i++) {
      if (c1[i] == c2[i] && c1[i] == SNAPSHOT_VALUE) return 0;
      if (c1[i] == SNAPSHOT_VALUE) return 1;
      if (c2[i] == SNAPSHOT_VALUE) return -1;

      int result = c1[i] - c2[i];
      if (result != 0) return result;
    }
    return c1.length - c2.length;
  }

  @NotNull
  public String getProductCode() {
    return myProductCode;
  }

  public int getBaselineVersion() {
    return myComponents[0];
  }

  @Deprecated
  public int getBuildNumber() {
    return myComponents[1];
  }

  @NotNull
  public int[] getComponents() {
    return myComponents;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BuildNumber that = (BuildNumber)o;

    if (!myProductCode.equals(that.myProductCode)) return false;
    if (!Arrays.equals(myComponents, that.myComponents)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myProductCode.hashCode();
    result = 31 * result + Arrays.hashCode(myComponents);
    return result;
  }

  // See http://www.jetbrains.net/confluence/display/IDEADEV/Build+Number+Ranges for historic build ranges
  private static int getBaseLineForHistoricBuilds(int bn) {
    if (bn >= 10000) {
      return 88; // Maia, 9x builds
    }

    if (bn >= 9500) {
      return 85; // 8.1 builds
    }

    if (bn >= 9100) {
      return 81; // 8.0.x builds
    }

    if (bn >= 8000) {
      return 80; // 8.0, including pre-release builds
    }

    if (bn >= 7500) {
      return 75; // 7.0.2+
    }

    if (bn >= 7200) {
      return 72; // 7.0 final
    }

    if (bn >= 6900) {
      return 69; // 7.0 pre-M2
    }

    if (bn >= 6500) {
      return 65; // 7.0 pre-M1
    }

    if (bn >= 6000) {
      return 60; // 6.0.2+
    }

    if (bn >= 5000) {
      return 55; // 6.0 branch, including all 6.0 EAP builds
    }

    if (bn >= 4000) {
      return 50; // 5.1 branch
    }

    return 40;
  }

  public boolean isSnapshot() {
    for (int each : myComponents) {
      if (each == SNAPSHOT_VALUE) return true;
    }
    return false;
  }
}