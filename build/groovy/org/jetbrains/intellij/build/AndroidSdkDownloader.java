// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build;

import com.intellij.openapi.util.SystemInfo;
import java.net.URI;
import java.nio.file.Path;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesConstants;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesManualRunOnly;

public class AndroidSdkDownloader {
  private static final String ANDROID_LAYOUTLIB_VERSION = "27.2.0.1";
  private static final String ANDROID_SDK_VERSION = "4.2.0.0";

  public static Path downloadSdk(BuildDependenciesCommunityRoot communityRoot) {
    Path androidSdkRoot = communityRoot.getCommunityRoot().resolve("build/dependencies/build/android-sdk");

    BuildDependenciesDownloader.extractFile(
      downloadAndroidLayoutLib(communityRoot),
      androidSdkRoot.resolve("layoutlib"),
      communityRoot
    );

    //noinspection SpellCheckingInspection
    BuildDependenciesDownloader.extractFile(
      downloadAndroidSdk(communityRoot),
      androidSdkRoot.resolve("prebuilts/studio/sdk"),
      communityRoot
    );

    return androidSdkRoot;
  }

  // debug only
  public static void main(String[] args) {
    Path root = downloadSdk(BuildDependenciesManualRunOnly.getCommunityRootFromWorkingDirectory());
    BuildDependenciesDownloader.info("Sdk is at " + root);
  }

  private static Path downloadAndroidLayoutLib(BuildDependenciesCommunityRoot communityRoot) {
    URI uri = BuildDependenciesDownloader.getUriForMavenArtifact(
      BuildDependenciesConstants.INTELLIJ_DEPENDENCIES_URL,
      "org.jetbrains.intellij.deps.android.tools.base",
      "layoutlib-resources",
      ANDROID_LAYOUTLIB_VERSION,
      "jar"
    );

    return BuildDependenciesDownloader.downloadFileToCacheLocation(communityRoot, uri);
  }

  private static Path downloadAndroidSdk(BuildDependenciesCommunityRoot communityRoot) {
    URI uri = BuildDependenciesDownloader.getUriForMavenArtifact(
      BuildDependenciesConstants.INTELLIJ_DEPENDENCIES_URL,
      "org.jetbrains.intellij.deps.android",
      "android-sdk",
      getOsPrefix() + "." + ANDROID_SDK_VERSION,
      "tar.gz"
    );

    return BuildDependenciesDownloader.downloadFileToCacheLocation(communityRoot, uri);
  }

  private static String getOsPrefix() {
    if (SystemInfo.isWindows) {
      return "windows";
    }

    if (SystemInfo.isLinux) {
      return "linux";
    }

    if (SystemInfo.isMac) {
      return "darwin";
    }

    throw new IllegalStateException("Unsupported operating system");
  }
}
