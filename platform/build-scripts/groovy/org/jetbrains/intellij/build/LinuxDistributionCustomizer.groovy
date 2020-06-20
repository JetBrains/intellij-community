// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic

@CompileStatic
abstract class LinuxDistributionCustomizer {
  /**
   * Path to a 128x128 png product icon for Linux distribution
   */
  String iconPngPath

  /**
   * Path to png file for EAP builds (if {@code null} {@link #iconPngPath} will be used)
   */
  String iconPngPathForEAP = null

  /**
   * Relative paths to files in Linux distribution which should take 'executable' permissions
   */
  List<String> extraExecutables = []

  /**
   * When {@code true}, the launcher script will suggest downloading an x86 JRE when launched on a corresponding OS.
   */
  boolean includeX86Files = true

  /**
   * If {@code true} a separate *-no-jdk.tar.gz artifact without JRE will be produced.
   */
  boolean buildTarGzWithoutBundledJre = true

  /**
   * If {@code true}, the only *-no-jbr.tar.gz will be produced, no other binaries for Linux will be built.
   */
  boolean buildOnlyBareTarGz = false

  /**
   * Set both properties if a .snap package should be produced.
   * "snapName" is the name of the package (e.g. "intellij-idea-ultimate", "pycharm-community").
   * "snapDescription" is the plain text description of the package.
   */
  String snapName = null
  String snapDescription = null

  /**
   * Name of the root directory inside linux .tar.gz archive
   */
  String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) {
    "${applicationInfo.productName}-${applicationInfo.isEAP ? buildNumber : applicationInfo.fullVersion}"
  }

  /**
   * Override this method to copy additional files to Linux distribution of the product.
   * @param targetDirectory contents of this directory will be packed into .tar.gz archive under {@link #getRootDirectoryName(ApplicationInfoProperties, String)}
   */
  void copyAdditionalFiles(BuildContext context, String targetDirectory) {}
}