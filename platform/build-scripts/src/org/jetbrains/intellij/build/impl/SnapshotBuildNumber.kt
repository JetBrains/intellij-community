// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.BuildPaths
import java.nio.file.Files
import java.nio.file.Path

object SnapshotBuildNumber {
  val PATH: Path by lazy {
    BuildPaths.COMMUNITY_ROOT.communityRoot.resolve("build.txt")
  }

  private const val SNAPSHOTS_SUFFIX = ".SNAPSHOT"

  /**
   * `${BASE}.SNAPSHOT`, specified in [PATH]
   */
  val VALUE: String by lazy {
    val snapshotBuildNumber = Files.readString(PATH).trim()
    check(snapshotBuildNumber.endsWith(SNAPSHOTS_SUFFIX)) {
      "$PATH: '$snapshotBuildNumber' is expected to have a '$SNAPSHOTS_SUFFIX' suffix"
    }
    snapshotBuildNumber
  }

  /**
   * [VALUE] without [SNAPSHOTS_SUFFIX]
   */
  val BASE: String by lazy {
    VALUE.removeSuffix(SNAPSHOTS_SUFFIX)
  }
}