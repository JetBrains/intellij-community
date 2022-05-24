// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import java.nio.file.Path

interface OsSpecificDistributionBuilder {
  val targetOs: OsFamily

  fun copyFilesForOsDistribution(targetPath: Path, arch: JvmArchitecture)

  fun buildArtifacts(osAndArchSpecificDistPath: Path, arch: JvmArchitecture)

  fun generateExecutableFilesPatterns(includeJre: Boolean): List<String> = emptyList()

  fun getArtifactNames(context: BuildContext): List<String> = emptyList()
}