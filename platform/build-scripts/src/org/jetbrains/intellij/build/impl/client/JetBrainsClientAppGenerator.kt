// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.impl.getMacZipRoot
import org.jetbrains.intellij.build.impl.substitutePlaceholdersInInfoPlist
import org.jetbrains.intellij.build.impl.targetIcnsFileName
import org.jetbrains.intellij.build.io.copyFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.writeText

/**
 * Generates a separate bundle for JetBrains Client in macOS distribution. Executable and icns files in the bundle are symlinks to
 * corresponding files in the main IDE bundle. A separate bundle is needed to ensure that macOS doesn't prevent a regular IDE instance from
 * running if the JetBrains Client application is active.
 * 
 * A shell script is used as the main executable in the embedded bundle (we cannot use symlink because in that case 'codesign' will fail
 * with "the main executable or Info.plist must be a regular file" error). The shell script executes a symlink to the main IDE's executable
 * (we cannot execute the main executable directly, because in that cast macOS will consider the started process as an instance of the 
 * main IDE application).
 */
internal suspend fun generateJetBrainsClientAppBundleForMacOs(mainAppContentsDir: Path, arch: JvmArchitecture,
                                                              jetbrainsClientBuildContext: BuildContext, mainIdeBuildContext: BuildContext) {
  withContext(Dispatchers.IO) {
    val jetBrainsClientMacCustomizer = jetbrainsClientBuildContext.macDistributionCustomizer!!
    val relativeContentsPath = "Helpers/${getMacZipRoot(jetBrainsClientMacCustomizer, jetbrainsClientBuildContext)}"
    val contentsDir = mainAppContentsDir / relativeContentsPath 
    copyFile(mainIdeBuildContext.paths.communityHomeDir / "platform/build-scripts/resources/mac/Contents/Info.plist", contentsDir / "Info.plist")
    val mainIdeExecutableFileName = mainIdeBuildContext.productProperties.baseFileName
    val shExecutableFileName = "${mainIdeExecutableFileName}.sh"
    val icnsFileName = mainIdeBuildContext.productProperties.targetIcnsFileName
    substitutePlaceholdersInInfoPlist(contentsDir, null, arch, jetBrainsClientMacCustomizer, jetbrainsClientBuildContext,
                                      executableFileName = shExecutableFileName, icnsFileName = icnsFileName)

    val macOsDir = contentsDir / "MacOS"
    Files.createDirectories(macOsDir)
    Files.createSymbolicLink(macOsDir / mainIdeExecutableFileName, Path("../../../../MacOS/$mainIdeExecutableFileName"))
    val executableSh = macOsDir / shExecutableFileName
    executableSh.writeText("""
       |#!/bin/sh
       |CLIENT_APP_BIN_DIR="${'$'}{0%/*}"
       |exec "${'$'}CLIENT_APP_BIN_DIR/$mainIdeExecutableFileName" "${'$'}@"
    """.trimMargin())
    try {
      val permissions = Files.getPosixFilePermissions(executableSh).toMutableSet()
      permissions.addAll(arrayOf(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_EXECUTE))
      Files.setPosixFilePermissions(executableSh, permissions)
    }
    catch (e: Exception) {
      mainIdeBuildContext.messages.info("Failed to set executable permissions for $executableSh in unpacked directory: $e")
    }
    mainIdeBuildContext.addExtraExecutablePattern(OsFamily.MACOS, "$relativeContentsPath/MacOS/$shExecutableFileName")

    val resourcesDir = contentsDir / "Resources"
    Files.createDirectories(resourcesDir)
    Files.createSymbolicLink(resourcesDir / icnsFileName, Path("../../../../Resources/$icnsFileName"))
  }
}