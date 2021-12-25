// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader

object AndroidSdkDownloaderBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    val communityHome = IdeaProjectLoaderUtil.guessCommunityHome(javaClass)
    val sdk = AndroidSdkDownloader.downloadSdk(BuildDependenciesCommunityRoot(communityHome))
    BuildDependenciesDownloader.info("Android Sdk root is at $sdk")
  }
}