// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.io.copyFileToDir
import org.jetbrains.jps.model.library.JpsOrderRootType
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal fun generateBuildTxt(context: BuildContext, targetDirectory: Path) {
  Files.writeString(targetDirectory.resolve("build.txt"), context.fullBuildNumber)
}

internal fun copyDistFiles(context: BuildContext, newDir: Path, os: OsFamily, arch: JvmArchitecture) {
  for (item in context.getDistFiles(os, arch)) {
    val targetFile = newDir.resolve(item.relativePath)
    Files.createDirectories(targetFile.parent)
    Files.copy(item.file, targetFile, StandardCopyOption.REPLACE_EXISTING)
  }
}

internal fun addDbusJava(context: CompilationContext, libDir: Path): List<String> {
  val library = context.findRequiredModule("intellij.platform.credentialStore").libraryCollection.findLibrary("dbus-java")!!
  val extraJars = ArrayList<String>()
  Files.createDirectories(libDir)
  for (file in library.getFiles(JpsOrderRootType.COMPILED)) {
    copyFileToDir(file.toPath(), libDir)
    extraJars.add(file.name)
  }
  return extraJars
}

internal fun copyInspectScript(context: BuildContext, distBinDir: Path) {
  val inspectScript = context.productProperties.inspectCommandName
  if (inspectScript != "inspect") {
    val targetPath = distBinDir.resolve("$inspectScript.sh")
    Files.move(distBinDir.resolve("inspect.sh"), targetPath, StandardCopyOption.REPLACE_EXISTING)
    context.patchInspectScript(targetPath)
  }
}