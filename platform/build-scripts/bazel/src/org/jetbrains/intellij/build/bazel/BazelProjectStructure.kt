// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.application.PathManager
import java.nio.file.Path
import kotlin.io.path.*

fun main() {
  reportBuildFileStructure(Path.of(PathManager.getHomePath()))
}

private fun reportBuildFileStructure(projectDir: Path) {
  val output = mutableListOf<String>()
  val root = BuildNode(projectDir)
  findBuildFiles(path = projectDir, parent = root, output = output, projectDir = projectDir)
  output.sort()
  output.forEach { println(it) }
}

private data class BuildNode(val path: Path, val children: MutableList<BuildNode> = ArrayList())

private fun findBuildFiles(path: Path, parent: BuildNode, output: MutableList<String>, projectDir: Path) {
  if (path.name == "out") {
    return
  }
  val buildFiles = path.listDirectoryEntries().filter {
    it.isRegularFile()
    && (it.name.uppercase() == "BUILD" ||
        (it.nameWithoutExtension.uppercase() == "BUILD" && (
          it.extension.lowercase() == "bzl" || it.extension.lowercase() == "bazel")))
  }
  if (buildFiles.size > 1) {
    error("multiple BUILD files in folder: '${path.absolutePathString()}'")
  }
  val currentBuildNode = if (buildFiles.size == 1) {
    val buildFile = BuildNode(path)
    output.add(buildFiles[0].relativeTo(projectDir).toString())
    parent.children.add(buildFile)
    buildFile
  }
  else {
    parent
  }
  for (e in path.listDirectoryEntries().filter { it.isDirectory() }) {
    findBuildFiles(path = e, parent = currentBuildNode, output = output, projectDir = projectDir)
  }
}
