// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import java.nio.file.Path

abstract class OsSpecificDistributionBuilder(@JvmField protected val buildContext: BuildContext) {
  abstract val targetOs: OsFamily

  abstract fun copyFilesForOsDistribution(targetPath: Path, arch: JvmArchitecture?)

  abstract fun buildArtifacts(osAndArchSpecificDistPath: Path, arch: JvmArchitecture)

  open fun generateExecutableFilesPatterns(includeJre: Boolean): List<String> = emptyList()

  open fun getArtifactNames(context: BuildContext): List<String> = emptyList()
}