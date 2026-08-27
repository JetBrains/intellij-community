// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.util.text.SemVer
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.impl.SnapshotBuildNumber.PATH
import org.jetbrains.intellij.build.impl.SnapshotBuildNumber.SNAPSHOT_SUFFIX
import org.jetbrains.intellij.build.impl.SnapshotBuildNumber.VALUE
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

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

/**
 * The plugin version a build stamps: [buildNumber] with its `.SNAPSHOT` suffix replaced by the build date, plus `.0`
 * when the result is a nightly.
 *
 * Shared by the two producers of a patched plugin descriptor. `BuildContextImpl.pluginBuildNumber` is the assembly's
 * reader, and `DevDistPluginDescriptorMain` is the packing action's. One function means the two cannot disagree on a
 * version string, which is what the byte gate would otherwise have to catch.
 */
internal fun computePluginBuildNumber(buildNumber: String, buildDateInSeconds: Long): String {
  var value = buildNumber
  if (value.endsWith(SNAPSHOT_SUFFIX)) {
    val buildDate = ZonedDateTime.ofInstant(Instant.ofEpochSecond(buildDateInSeconds), ZoneOffset.UTC)
    value = value.replace(SNAPSHOT_SUFFIX, "." + PLUGIN_DATE_FORMAT.format(buildDate))
  }
  if (value.count { it == '.' } <= 1) {
    value = "$value.0"
  }
  check(SemVer.parseFromText(value) != null) {
    "The plugin build number $value is expected to match the Semantic Versioning, see https://semver.org"
  }
  return value
}

private val PLUGIN_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
