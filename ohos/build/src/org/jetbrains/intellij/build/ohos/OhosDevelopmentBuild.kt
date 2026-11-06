// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.ohos

import com.intellij.openapi.util.io.NioFiles
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildLifetime
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.impl.buildDistributions
import org.jetbrains.intellij.build.impl.getOhosAppDirectory
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.runProcess
import java.nio.file.Files
import java.nio.file.Path

/**
 * Builds the OHOS app of a product for development and copies it to [destination].
 * The build targets only OHOS and skips the `.tar.gz` archive of the app.
 * [createContext] creates the build context of the product. The copy replaces [destination].
 */
fun buildOhosAppForDevelopment(options: BuildOptions, destination: Path, createContext: (BuildOptions, BuildLifetime) -> BuildContext) {
  val developmentOptions = options.copy(buildStepsToSkip = options.buildStepsToSkip + BuildOptions.OHOS_TAR_GZ_STEP)
  developmentOptions.targetOs = persistentListOf(OsFamily.OHOS)
  BuildLifetime().use { lifetime ->
    val context = createContext(developmentOptions, lifetime)
    buildDistributions(context)
    copyApp(getOhosAppDirectory(context, JvmArchitecture.aarch64), destination, context)
  }
}

private fun copyApp(appDir: Path, destination: Path, context: BuildContext) {
  check(Files.isDirectory(appDir)) { "The OHOS app wasn't built: no $appDir" }
  NioFiles.deleteRecursively(destination)
  if (OsFamily.currentOs == OsFamily.MACOS) {
    // an APFS clone, which takes no time and space despite the size of the IDE
    runProcess(args = listOf("cp", "-Rc", appDir.toString(), destination.toString()))
  }
  else {
    copyDir(appDir, destination)
  }
  context.messages.info("Copied ${appDir.fileName} to $destination")
}
