// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.support

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.system.CpuArch
import groovy.transform.CompileStatic
import io.opentelemetry.api.trace.Span
import kotlin.Unit
import kotlin.jvm.functions.Function0
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildContextKt
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.dependencies.TeamCityHelper
import org.jetbrains.intellij.build.io.ProcessKt

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.function.Consumer

import static java.nio.file.attribute.PosixFilePermission.*
import static org.jetbrains.intellij.build.TraceManager.spanBuilder

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

  static synchronized void bundle(BuildContext context, OsFamily os, JvmArchitecture arch, Path distributionDir) {
    BuildContextKt.executeStep(context, spanBuilder("bundle repair-utility")
                               .setAttribute("os", os.osName), BuildOptions.REPAIR_UTILITY_BUNDLE_STEP, new Function0<Unit>() {
      @Override
      Unit invoke() {
        Map<Binary, Path> cache = BINARIES_CACHE
        if (cache == null) {
          cache = buildBinaries(context)
          BINARIES_CACHE = cache
        }

        if (cache.isEmpty()) {
          return
        }

        Binary binary = findBinary(context, os, arch)
        Path path = cache.get(binary)
        if (path == null) {
          context.messages.error("No binary was built for $os and $arch")
        }

        Path repairUtilityTarget = distributionDir.resolve(binary.relativeTargetPath)
        Span.current().addEvent("copy $path to $repairUtilityTarget")
        Files.createDirectories(repairUtilityTarget.parent)
        Files.copy(path, repairUtilityTarget, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
        return Unit.INSTANCE
      }
    })
  }

  static synchronized void generateManifest(BuildContext context, Path unpackedDistribution, String manifestFileNamePrefix) {
    BuildContextKt.executeStep(context, spanBuilder("generate installation integrity manifest")
                               .setAttribute("dir", unpackedDistribution.toString()), BuildOptions.REPAIR_UTILITY_BUNDLE_STEP, new Function0<Unit>() {
      @Override
      Unit invoke() {
        if (Files.notExists(unpackedDistribution)) {
          context.messages.error("$unpackedDistribution doesn't exist")
        }

        if (BINARIES_CACHE == null) {
          BINARIES_CACHE = buildBinaries(context)
        }
        if (BINARIES_CACHE.isEmpty()) {
          return
        }
        OsFamily currentOs = SystemInfoRt.isWindows ? OsFamily.WINDOWS :
                             SystemInfoRt.isMac ? OsFamily.MACOS :
                             SystemInfoRt.isLinux ? OsFamily.LINUX : null
        Binary binary = findBinary(context, currentOs, CpuArch.isArm64() ? JvmArchitecture.aarch64 : JvmArchitecture.x64)
        def binaryPath = repairUtilityProjectHome(context).resolve(binary.relativeSourcePath)
        def tmpDir = context.paths.tempDir.resolve(BuildOptions.REPAIR_UTILITY_BUNDLE_STEP + UUID.randomUUID().toString())
        Files.createDirectories(tmpDir)
        try {
          ProcessKt.runProcess([binaryPath.toString(), 'hashes', '-g', '--path', unpackedDistribution.toString()], tmpDir, context.messages)
        }
        catch (Throwable e) {
          context.messages.warning("Manifest generation failed, listing unpacked distribution content for debug:")
          Files.walk(unpackedDistribution).withCloseable { files ->
            files.forEach({ context.messages.warning(it.toString()) } as Consumer<Path>)
          }
          throw e
        }

        Path manifest = tmpDir.resolve("manifest.json")
        if (Files.notExists(manifest)) {
          Path repairLog = tmpDir.resolve("repair.log")
          context.messages.error("Unable to generate installation integrity manifest: ${Files.readString(repairLog)}")
        }

        Path artifact = context.paths.artifactDir.resolve("${manifestFileNamePrefix}.manifest")
        Files.move(manifest, artifact, StandardCopyOption.REPLACE_EXISTING)
        return Unit.INSTANCE
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
          ProcessKt.runProcess(['docker', '--version'], null, buildContext.messages)
          ProcessKt.runProcess(['bash', 'build.sh'], projectHome, buildContext.messages)
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
        def executablePermissions = EnumSet.of(
          OWNER_READ, OWNER_WRITE, OWNER_EXECUTE, GROUP_READ, GROUP_EXECUTE, OTHERS_READ, OTHERS_EXECUTE
        )
        for (Path file in binaries.values()) {
          if (Files.notExists(file)) {
            buildContext.messages.error("$file doesn't exist")
          }
          Files.setPosixFilePermissions(file, executablePermissions)
        }
        return binaries
      }
    }
  }
}
