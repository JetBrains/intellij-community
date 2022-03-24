// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.support

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.system.CpuArch
import groovy.transform.CompileStatic
import io.opentelemetry.api.trace.Span
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.dependencies.TeamCityHelper
import org.jetbrains.intellij.build.impl.BuildHelper

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.function.Consumer

import static org.jetbrains.intellij.build.impl.TracerManager.spanBuilder
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
final class RepairUtilityBuilder {
  private static volatile Map<Binary, Path> BINARIES_CACHE

  private static final Collection<Binary> BINARIES = List.of(
    new Binary(OsFamily.LINUX, JvmArchitecture.x64, 'bin/repair-linux-amd64', 'bin/repair'),
    new Binary(OsFamily.WINDOWS, JvmArchitecture.x64, 'bin/repair.exe', 'bin/repair.exe'),
    new Binary(OsFamily.MACOS, JvmArchitecture.x64, 'bin/repair-darwin-amd64', 'bin/repair'),
    new Binary(OsFamily.MACOS, JvmArchitecture.aarch64, 'bin/repair-darwin-arm64', 'bin/repair')
  )

  static final class Binary {
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

  static synchronized void bundle(BuildContext buildContext, OsFamily os, JvmArchitecture arch, Path distributionDir) {
    buildContext.executeStep(spanBuilder("bundle repair-utility")
                               .setAttribute("os", os.osName), BuildOptions.REPAIR_UTILITY_BUNDLE_STEP, new Runnable() {
      @Override
      void run() {
        Map<Binary, Path> cache = BINARIES_CACHE
        if (cache == null) {
          cache = buildBinaries(buildContext)
          BINARIES_CACHE = cache
        }

        if (cache.isEmpty()) {
          return
        }

        Binary binary = findBinary(buildContext, os, arch)
        Path path = cache.get(binary)
        if (path == null) {
          buildContext.messages.error("No binary was built for $os and $arch")
        }

        Path repairUtilityTarget = distributionDir.resolve(binary.relativeTargetPath)
        Span.current().addEvent("copy $path to $repairUtilityTarget")
        Files.createDirectories(repairUtilityTarget.parent)
        Files.copy(path, repairUtilityTarget)
      }
    })
  }

  static synchronized void generateManifest(BuildContext buildContext, Path unpackedDistribution, String manifestFileNamePrefix) {
    buildContext.executeStep(spanBuilder("generate installation integrity manifest")
                               .setAttribute("dir", unpackedDistribution.toString()), BuildOptions.REPAIR_UTILITY_BUNDLE_STEP, new Runnable() {
      @Override
      void run() {
        if (Files.notExists(unpackedDistribution)) {
          buildContext.messages.error("$unpackedDistribution doesn't exist")
        }

        if (BINARIES_CACHE == null) {
          BINARIES_CACHE = buildBinaries(buildContext)
        }
        if (BINARIES_CACHE.isEmpty()) {
          return
        }
        OsFamily currentOs = SystemInfoRt.isWindows ? OsFamily.WINDOWS :
                             SystemInfoRt.isMac ? OsFamily.MACOS :
                             SystemInfoRt.isLinux ? OsFamily.LINUX : null
        Binary binary = findBinary(buildContext, currentOs, CpuArch.isArm64() ? JvmArchitecture.aarch64 : JvmArchitecture.x64)
        def binaryPath = repairUtilityProjectHome(buildContext).resolve(binary.relativeSourcePath)
        def tmpDir = buildContext.paths.tempDir.resolve(BuildOptions.REPAIR_UTILITY_BUNDLE_STEP + UUID.randomUUID().toString())
        Files.createDirectories(tmpDir)
        try {
          BuildHelper.runProcess(buildContext, [binaryPath.toString(), 'hashes', '-g', '--path', unpackedDistribution.toString()], tmpDir)
        }
        catch (Throwable e) {
          buildContext.messages.warning("Manifest generation failed, listing unpacked distribution content for debug:")
          Files.walk(unpackedDistribution).withCloseable { files ->
            files.forEach({ buildContext.messages.warning(it.toString()) } as Consumer<Path>)
          }
          throw e
        }

        Path manifest = tmpDir.resolve("manifest.json")
        if (Files.notExists(manifest)) {
          Path repairLog = tmpDir.resolve("repair.log")
          buildContext.messages.error("Unable to generate installation integrity manifest: ${Files.readString(repairLog)}")
        }

        Path artifact = buildContext.paths.artifactDir.resolve("${manifestFileNamePrefix}.manifest")
        Files.move(manifest, artifact, StandardCopyOption.REPLACE_EXISTING)
      }
    })
  }

  @Nullable
  static synchronized Binary binaryFor(BuildContext buildContext, OsFamily os, JvmArchitecture arch) {
    if (!buildContext.options.buildStepsToSkip.contains(BuildOptions.REPAIR_UTILITY_BUNDLE_STEP)) {
      Map<Binary, Path> cache = BINARIES_CACHE
      if (cache == null) {
        cache = buildBinaries(buildContext)
        BINARIES_CACHE = cache
      }
      if (!cache.isEmpty()) {
        return findBinary(buildContext, os, arch)
      }
    }
    return null
  }

  private static Binary findBinary(BuildContext buildContext, OsFamily os, JvmArchitecture arch) {
    Binary binary = BINARIES.find { it.os == os && it.arch == arch }
    if (binary == null) {
      buildContext.messages.error("Unsupported binary: $os $arch")
    }
    return binary
  }

  private static Path repairUtilityProjectHome(BuildContext buildContext) {
    Path projectHome = buildContext.paths.communityHomeDir.parent.resolve('build/support/repair-utility')
    if (Files.notExists(projectHome)) {
      buildContext.messages.warning("$projectHome doesn't exist")
      projectHome = null
    }
    return projectHome
  }

  private static Map<Binary, Path> buildBinaries(BuildContext buildContext) {
    buildContext.messages.block("build repair-utility") {
      if (SystemInfoRt.isWindows) {
        // FIXME: Linux containers on Windows should be fine
        buildContext.messages.warning("Cannot be built on Windows")
        return Collections.<Binary, Path>emptyMap()
      }

      Path projectHome = repairUtilityProjectHome(buildContext)
      if (projectHome == null) {
        return Collections.<Binary, Path>emptyMap()
      }
      else {
        try {
          BuildHelper.runProcess(buildContext, ['docker', '--version'])
          BuildHelper.runProcess(buildContext, ['bash', 'build.sh'], projectHome)
        }
        catch (Throwable e) {
          if (TeamCityHelper.isUnderTeamCity) {
            throw e
          }
          return Collections.<Binary, Path>emptyMap()
        }

        Map<Binary, Path> binaries = BINARIES.collectEntries {
          [(it): projectHome.resolve(it.relativeSourcePath)]
        }
        for (Path file in binaries.values()) {
          if (Files.notExists(file)) {
            buildContext.messages.error("$file doesn't exist")
          }
        }
        return binaries
      }
    }
  }
}
