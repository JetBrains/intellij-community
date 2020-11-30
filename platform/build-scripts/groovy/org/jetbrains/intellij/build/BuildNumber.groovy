// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

@CompileStatic
final class BuildNumber implements Comparable<BuildNumber> {
  private static final String STAR = "*"
  private static final String SNAPSHOT = "SNAPSHOT"

  public static final int SNAPSHOT_VALUE = Integer.MAX_VALUE

  @NotNull private final String myProductCode
  private final int[] myComponents

  BuildNumber(@NotNull String productCode, int[] components) {
    myProductCode = productCode
    myComponents = components
  }

  private static boolean isPlaceholder(@NotNull String value) {
    return "__BUILD_NUMBER__" == value || "__BUILD__" == value
  }

  @NotNull
  String asString() {
    return asString(true, true)
  }

  @NotNull
  protected String asString(boolean includeProductCode, boolean withSnapshotMarker) {
    StringBuilder builder = new StringBuilder()

    if (includeProductCode && !myProductCode.isEmpty()) {
      builder.append(myProductCode).append('-')
    }

    for (int each : myComponents) {
      if (each != SNAPSHOT_VALUE) {
        builder.append(each)
      }
      else if (withSnapshotMarker) {
        builder.append(SNAPSHOT)
      }
      builder.append('.')
    }
    if (builder.charAt(builder.length() - 1) == '.' as char) {
      builder.setLength(builder.length() - 1)
    }
    return builder.toString()
  }

  static @Nullable
  BuildNumber fromString(@Nullable String version, @NotNull String current) {
    if (version == null) {
      return null
    }
    version = version.trim()
    return version.isEmpty() ? null : fromString(version, null, null, current)
  }


  static @Nullable
  BuildNumber fromString(@NotNull String version,
                         @Nullable String pluginName,
                         @Nullable String productCodeIfAbsentInVersion,
                         @NotNull String current) {
    String code = version
    int productSeparator = code.indexOf('-')
    String productCode
    if (productSeparator > 0) {
      productCode = code.substring(0, productSeparator)
      code = code.substring(productSeparator + 1)
    }
    else {
      productCode = productCodeIfAbsentInVersion != null ? productCodeIfAbsentInVersion : ""
    }

    if (SNAPSHOT == code || isPlaceholder(code)) {
      return new BuildNumber(productCode, fromString(current, current).myComponents)
    }

    int baselineVersionSeparator = code.indexOf('.')

    if (baselineVersionSeparator > 0) {
      String baselineVersionString = code.substring(0, baselineVersionSeparator)
      if (baselineVersionString.trim().isEmpty()) {
        return null
      }

      String[] stringComponents = code.split("\\.")
      int[] intComponentsList = new int[stringComponents.length]
      int n = stringComponents.length
      for (int i = 0; i < n; i++) {
        String stringComponent = stringComponents[i]
        int comp = parseBuildNumber(version, stringComponent, pluginName)
        intComponentsList[i] = comp
        if (comp == SNAPSHOT_VALUE && (i + 1) != n) {
          intComponentsList = Arrays.copyOf(intComponentsList, i + 1)
          break
        }
      }
      return new BuildNumber(productCode, intComponentsList)
    }
    else {
      int buildNumber = parseBuildNumber(version, code, pluginName)
      if (buildNumber <= 2000) {
        // it's probably a baseline, not a build number
        return new BuildNumber(productCode, buildNumber, 0)
      }

      int baselineVersion = getBaseLineForHistoricBuilds(buildNumber)
      return new BuildNumber(productCode, baselineVersion, buildNumber)
    }
  }

  private static int parseBuildNumber(String version, @NotNull String code, String pluginName) {
    if (SNAPSHOT == code || isPlaceholder(code) || STAR == code) {
      return SNAPSHOT_VALUE
    }

    try {
      return Integer.parseInt(code)
    }
    catch (NumberFormatException e) {
      throw new RuntimeException("Invalid version number: " + version + "; plugin name: " + pluginName)
    }
  }

  @Override
  int compareTo(@NotNull BuildNumber o) {
    int[] c1 = myComponents
    int[] c2 = o.myComponents

    for (int i = 0; i < Math.min(c1.length, c2.length); i++) {
      if (c1[i] == c2[i] && c1[i] == SNAPSHOT_VALUE) return 0
      if (c1[i] == SNAPSHOT_VALUE) return 1
      if (c2[i] == SNAPSHOT_VALUE) return -1
      int result = c1[i] - c2[i]
      if (result != 0) return result
    }

    return c1.length - c2.length
  }

  @Override
  boolean equals(Object o) {
    if (this == o) return true
    if (o == null || getClass() != o.getClass()) return false

    BuildNumber that = (BuildNumber)o

    if (myProductCode != that.myProductCode) return false
    return Arrays.equals(myComponents, that.myComponents)
  }

  @Override
  int hashCode() {
    int result = myProductCode.hashCode()
    result = 31 * result + Arrays.hashCode(myComponents)
    return result
  }

  @Override
  String toString() {
    return asString()
  }

  // http://www.jetbrains.org/intellij/sdk/docs/basics/getting_started/build_number_ranges.html
  private static int getBaseLineForHistoricBuilds(int bn) {
    if (bn >= 10000) return 88 // Maia, 9x builds
    if (bn >= 9500) return 85  // 8.1 builds
    if (bn >= 9100) return 81  // 8.0.x builds
    if (bn >= 8000) return 80  // 8.0, including pre-release builds
    if (bn >= 7500) return 75  // 7.0.2+
    if (bn >= 7200) return 72  // 7.0 final
    if (bn >= 6900) return 69  // 7.0 pre-M2
    if (bn >= 6500) return 65  // 7.0 pre-M1
    if (bn >= 6000) return 60  // 6.0.2+
    if (bn >= 5000) return 55  // 6.0 branch, including all 6.0 EAP builds
    if (bn >= 4000) return 50  // 5.1 branch
    return 40
  }
}
