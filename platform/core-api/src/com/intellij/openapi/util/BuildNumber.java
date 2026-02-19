// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

public final class BuildNumber implements Comparable<BuildNumber> {
  private static final String STAR = "*";
  private static final String SNAPSHOT = "SNAPSHOT";
  private static final String FALLBACK_VERSION = "999.SNAPSHOT";

  public static final int SNAPSHOT_VALUE = Integer.MAX_VALUE;

  private final @NotNull String myProductCode;
  private final int @NotNull [] myComponents;

  public BuildNumber(@NotNull String productCode, int baselineVersion, int buildNumber) {
    this(productCode, new int[]{baselineVersion, buildNumber});
  }

  public BuildNumber(@NotNull String productCode, int @NotNull ... components) {
    myProductCode = productCode;
    myComponents = components;
  }

  private static boolean isPlaceholder(String value) {
    return "__BUILD_NUMBER__".equals(value) || "__BUILD__".equals(value);
  }

  /**
   * @return an empty string if unknown
   */
  public @NotNull String getProductCode() {
    return myProductCode;
  }

  public int getBaselineVersion() {
    return myComponents[0];
  }

  public int @NotNull [] getComponents() {
    return myComponents.clone();
  }

  public boolean isSnapshot() {
    int result = -1;
    for (int i = 0; i < myComponents.length; i++) {
      if (myComponents[i] == SNAPSHOT_VALUE) {
        result = i;
        break;
      }
    }
    return result != -1;
  }

  @Contract(pure = true)
  public @NotNull BuildNumber withoutProductCode() {
    return myProductCode.isEmpty() ? this : new BuildNumber("", myComponents);
  }

  public @NotNull @NlsSafe String asString() {
    return asString(true, true);
  }

  public @NotNull @NlsSafe String asStringWithoutProductCode() {
    return asString(false, true);
  }

  public @NotNull String asStringWithoutProductCodeAndSnapshot() {
    return asString(false, false);
  }

  private @NotNull String asString(boolean includeProductCode, boolean withSnapshotMarker) {
    StringBuilder builder = new StringBuilder();

    if (includeProductCode && !myProductCode.isEmpty()) {
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
    if (builder.charAt(builder.length() - 1) == '.') {
      builder.setLength(builder.length() - 1);
    }
    return builder.toString();
  }

  public static @Nullable BuildNumber fromPluginCompatibleBuild() {
    return fromString(getPluginCompatibleBuild());
  }

  /**
   * Attempts to parse the build number from the specified string.
   * Returns {@code null} if the string is not a valid build number.
   */
  public static @Nullable BuildNumber fromStringOrNull(@NotNull String version) {
    try {
      return fromString(version);
    }
    catch (RuntimeException ignored) {
      return null;
    }
  }

  public static @Nullable BuildNumber fromString(@Nullable String version) {
    if (version == null) {
      return null;
    }
    version = version.trim();
    return fromString(version, null, null);
  }

  public static @Nullable BuildNumber fromStringWithProductCode(@NotNull String version, @NotNull String productCode) {
    return fromString(version, null, productCode);
  }

  public static @Nullable BuildNumber fromString(@NotNull String version, @Nullable String pluginName, @Nullable String productCodeIfAbsentInVersion) {
    if (version.isEmpty()) {
      return null;
    }

    String code = version;
    int productSeparator = code.indexOf('-');
    String productCode;
    if (productSeparator > 0) {
      productCode = code.substring(0, productSeparator);
      code = code.substring(productSeparator + 1);
    }
    else {
      productCode = productCodeIfAbsentInVersion == null ? "" : productCodeIfAbsentInVersion;
    }

    if (SNAPSHOT.equals(code) || isPlaceholder(code)) {
      return new BuildNumber(productCode, currentVersion().myComponents);
    }

    int baselineVersionSeparator = code.indexOf('.');

    if (baselineVersionSeparator > 0) {
      String baselineVersionString = code.substring(0, baselineVersionSeparator);
      if (baselineVersionString.trim().isEmpty()) {
        return null;
      }

      String[] stringComponents = code.split("\\.");
      int[] intComponentList = new int[stringComponents.length];
      for (int i = 0, n = stringComponents.length; i < n; i++) {
        String stringComponent = stringComponents[i];
        int component = parseBuildNumber(version, stringComponent, pluginName);
        intComponentList[i] = component;
        if (component == SNAPSHOT_VALUE && (i + 1) != n) {
          intComponentList = Arrays.copyOf(intComponentList, i + 1);
          break;
        }
      }
      return new BuildNumber(productCode, intComponentList);
    }
    else {
      int buildNumber = parseBuildNumber(version, code, pluginName);
      if (buildNumber <= 2000) {
        // it's probably a baseline, not a build number
        return new BuildNumber(productCode, buildNumber, 0);
      }

      int baselineVersion = getBaseLineForHistoricBuilds(buildNumber);
      return new BuildNumber(productCode, baselineVersion, buildNumber);
    }
  }

  private static int parseBuildNumber(String version, @NotNull String code, String pluginName) {
    if (SNAPSHOT.equals(code) || isPlaceholder(code) || STAR.equals(code)) {
      return SNAPSHOT_VALUE;
    }

    try {
      return Integer.parseInt(code);
    }
    catch (NumberFormatException e) {
      throw new RuntimeException("Invalid version number: " + version + "; plugin name: " + pluginName);
    }
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BuildNumber that = (BuildNumber)o;

    if (!myProductCode.equals(that.myProductCode)) return false;
    return Arrays.equals(myComponents, that.myComponents);
  }

  @Override
  public int hashCode() {
    int result = myProductCode.hashCode();
    result = 31 * result + Arrays.hashCode(myComponents);
    return result;
  }

  @Override
  public String toString() {
    return asString();
  }

  // https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html
  private static int getBaseLineForHistoricBuilds(int bn) {
    if (bn >= 10000) return 88; // Maia, 9x builds
    if (bn >= 9500) return 85;  // 8.1 builds
    if (bn >= 9100) return 81;  // 8.0.x builds
    if (bn >= 8000) return 80;  // 8.0, including pre-release builds
    if (bn >= 7500) return 75;  // 7.0.2+
    if (bn >= 7200) return 72;  // 7.0 final
    if (bn >= 6900) return 69;  // 7.0 pre-M2
    if (bn >= 6500) return 65;  // 7.0 pre-M1
    if (bn >= 6000) return 60;  // 6.0.2+
    if (bn >= 5000) return 55;  // 6.0 branch, including all 6.0 EAP builds
    if (bn >= 4000) return 50;  // 5.1 branch
    return 40;
  }

  private static final Supplier<BuildNumber> CURRENT_VERSION = new SynchronizedClearableLazy<>(BuildNumber::fromFile);

  private static @NotNull BuildNumber fromFile() {
    String homePath = PathManager.getHomePath();
    Path home = Paths.get(homePath);

    BuildNumber result = readFile(home.resolve("build.txt"));
    if (result != null) {
      return result;
    }

    if (SystemInfoRt.isMac) {
      result = readFile(home.resolve("Resources/build.txt"));
      if (result != null) {
        return result;
      }
    }

    String communityHomePath = PathManager.getCommunityHomePath();
    if (!communityHomePath.equals(homePath)) {
      result = readFile(Paths.get(communityHomePath, "build.txt"));
      if (result != null) {
        return result;
      }
    }

    return Objects.requireNonNull(fromString(FALLBACK_VERSION));
  }

  private static @Nullable BuildNumber readFile(@NotNull Path path) {
    try (BufferedReader reader = Files.newBufferedReader(path)) {
      String text = reader.readLine();
      if (text != null) {
        return fromString(text);
      }
    }
    catch (IOException ignored) {
    }
    return null;
  }

  /**
   * This method is for internal platform use only. In regular code use {@link com.intellij.openapi.application.ApplicationInfo#getBuild()} instead.
   */
  @ApiStatus.Internal
  public static @NotNull BuildNumber currentVersion() {
    return CURRENT_VERSION.get();
  }

  private static @Nullable String getPluginCompatibleBuild() {
    return EarlyAccessRegistryManager.INSTANCE.getString("idea.plugins.compatible.build");
  }
}
