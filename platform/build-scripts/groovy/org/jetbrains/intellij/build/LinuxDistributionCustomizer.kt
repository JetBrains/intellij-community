// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import java.nio.file.Path

abstract class LinuxDistributionCustomizer {
  /**
   * Path to a 128x128 png product icon for Linux distribution
   */
  var iconPngPath: String? = null

  /**
   * Path to png file for EAP builds (if {@code null} {@link #iconPngPath} will be used)
   */
  var iconPngPathForEAP: String? = null

  /**
   * Relative paths to files in Linux distribution which should take 'executable' permissions
   */
  var extraExecutables: List<String> = emptyList()

  /**
   * If {@code true} a separate *-no-jdk.tar.gz artifact without runtime will be produced.
   */
  var buildTarGzWithoutBundledRuntime = true

  /**
   * If {@code true}, the only *-no-jbr.tar.gz will be produced, no other binaries for Linux will be built.
   */
  var buildOnlyBareTarGz = false

  /**
   * Set both properties if a .snap package should be produced.
   * "snapName" is the name of the package (e.g. "intellij-idea-ultimate", "pycharm-community").
   * "snapDescription" is the plain text description of the package.
   */
  var snapName: String? = null
  var snapDescription: String? = null

  /**
   * Name of the root directory inside linux .tar.gz archive
   */
  open fun getRootDirectoryName(applicationInfo: ApplicationInfoProperties, buildNumber: String): String =
    "${applicationInfo.productName}-${if (applicationInfo.isEAP) buildNumber else applicationInfo.fullVersion}"

  /**
   * Override this method to copy additional files to Linux distribution of the product.
   * @param targetDir contents of this directory will be packed into .tar.gz archive under {@link #getRootDirectoryName(ApplicationInfoProperties, String)}
   */
  open fun copyAdditionalFiles(context: BuildContext, targetDir: Path, arch: JvmArchitecture) {
  }
}
