// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.io.Decompressor
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.copyFileToDir
import org.jetbrains.jps.model.library.JpsOrderRootType
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal fun unpackPty4jNative(context: BuildContext, distDir: Path, pty4jOsSubpackageName: String?): Path {
  val pty4jNativeDir = distDir.resolve("lib/pty4j-native")
  val nativePkg = "resources/com/pty4j/native"
  var copied = false
  for (file in context.project.libraryCollection.findLibrary("pty4j")!!.getPaths(JpsOrderRootType.COMPILED)) {
    val tempDir = Files.createTempDirectory(context.paths.tempDir, file.fileName.toString())
    try {
      Decompressor.Zip(file).withZipExtensions().extract(tempDir)
      val nativeDir = tempDir.resolve(nativePkg)
      if (Files.isDirectory(nativeDir)) {
        Files.newDirectoryStream(nativeDir).use { stream ->
          for (child in stream) {
            val childName = child.fileName.toString()
            if (pty4jOsSubpackageName == null || pty4jOsSubpackageName == childName) {
              val dest = pty4jNativeDir.resolve(childName)
              copied = true
              copyDir(child, Files.createDirectories(dest))
            }
          }
        }
      }
    }
    finally {
      NioFiles.deleteRecursively(tempDir)
    }
  }

  if (!copied) {
    context.messages.error("Cannot layout pty4j native: no files extracted")
  }
  return pty4jNativeDir
}

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