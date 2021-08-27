// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.support

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.impl.BuildHelper

import java.nio.file.Files
import java.nio.file.Path

/**
 * Builds 'repair' command line utility which is a simple and automated way to fix the IDE when it cannot start:
 * <ul>
 * <li>generates or checks IDE installation integrity</li>
 * <li>checks the idea.log file for errors</li>
 * <li>checks if broken, or old plugins are installed</li>
 * <li>checks the runtime that starts IDE</li>
 * <li>checks for problems in .vmoptions file used to start the IDE</li>
 * </ul>
 *
 * <p>
 * Note: Bash and Docker are required to build the utility.
 * </p>
 */
@CompileStatic
class RepairUtilityBuilder {
  private static Map<Binary, Path> BINARIES_CACHE
  static final Collection<Binary> BINARIES = Collections.unmodifiableList([
    new Binary(OsFamily.LINUX, JvmArchitecture.x64, 'bin/repair-linux-amd64', 'bin/repair'),
    new Binary(OsFamily.WINDOWS, JvmArchitecture.x64, 'bin/repair.exe', 'bin/repair.exe'),
    new Binary(OsFamily.MACOS, JvmArchitecture.x64, 'bin/repair-darwin-amd64', 'bin/repair'),
    new Binary(OsFamily.MACOS, JvmArchitecture.aarch64, 'bin/repair-darwin-arm64', 'bin/repair')
  ])

  static class Binary {
    final OsFamily os
    final JvmArchitecture arch
    final String relativeTargetPath
    private final String relativeSourcePath

    private Binary(OsFamily os, JvmArchitecture arch, String relativeSourcePath, String relativeTargetPath) {
      this.os = os
      this.arch = arch
      this.relativeSourcePath = relativeSourcePath
      this.relativeTargetPath = relativeTargetPath
    }
  }

  static void bundle(BuildContext buildContext, OsFamily os, JvmArchitecture arch, Path distributionDir) {
    synchronized (RepairUtilityBuilder) {
      buildContext.executeStep("Bundling repair-utility for $os", BuildOptions.REPAIR_UTILITY_BUNDLE_STEP) {
        if (BINARIES_CACHE == null) {
          buildContext.messages.block("Building repair-utility") {
            BINARIES_CACHE = buildBinaries(buildContext)
          }
        }
        if (BINARIES_CACHE.isEmpty()) return
        Binary binary = BINARIES.find { it.os == os && it.arch == arch }
        if (binary == null) buildContext.messages.error("Unsupported binary: $os $arch")
        Path path = BINARIES_CACHE[binary]
        if (path == null) buildContext.messages.error("No binary was built for $os and $arch")
        Path repairUtilityTarget = distributionDir.resolve(binary.relativeTargetPath)
        buildContext.messages.info("Copying $path to $repairUtilityTarget")
        Files.createDirectories(repairUtilityTarget.parent)
        Files.copy(path, repairUtilityTarget)
      }
    }
  }

  private static Map<Binary, Path> buildBinaries(BuildContext buildContext) {
    def projectHome = buildContext.paths.projectHomeDir.resolve('build/support/repair-utility')
    if (!Files.exists(projectHome)) {
      buildContext.messages.warning("$projectHome doesn't exist")
      return [:]
    }
    else {
      BuildHelper.runProcess(buildContext, ['docker', '--version'])
      BuildHelper.runProcess(buildContext, ['bash', 'build.sh'], projectHome)
      Map<Binary, Path> binaries = BINARIES.collectEntries {
        [(it): projectHome.resolve(it.relativeSourcePath)]
      }
      binaries.values().each {
        if (!Files.exists(it)) {
          buildContext.messages.error("$it doesn't exist")
        }
      }
      return binaries
    }
  }
}
