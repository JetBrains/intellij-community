// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.BuildPaths
import java.nio.file.Files
import java.nio.file.Path

object SnapshotBuildNumber {
  val PATH: Path by lazy {
    BuildPaths.COMMUNITY_ROOT.communityRoot.resolve("build.txt")
  }

  const val SNAPSHOT_SUFFIX: String = ".SNAPSHOT"

  /**
   * `${BASE}.SNAPSHOT`, specified in [PATH]
   */
  val VALUE: String by lazy {
    val snapshotBuildNumber = Files.readString(PATH).trim()
    check(snapshotBuildNumber.endsWith(SNAPSHOT_SUFFIX)) {
      "$PATH: '$snapshotBuildNumber' is expected to have a '$SNAPSHOT_SUFFIX' suffix"
    }
    snapshotBuildNumber
  }

  /**
   * [VALUE] without [SNAPSHOT_SUFFIX]
   */
  val BASE: String by lazy {
    VALUE.removeSuffix(SNAPSHOT_SUFFIX)
  }
}