// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm

import com.google.devtools.build.lib.worker.WorkerProtocol
import org.jetbrains.intellij.build.io.AddDirEntriesMode
import org.jetbrains.intellij.build.io.PackageIndexBuilder
import org.jetbrains.intellij.build.io.W_OVERWRITE
import org.jetbrains.intellij.build.io.ZipArchiveOutputStream
import org.jetbrains.intellij.build.io.ZipIndexWriter
import org.jetbrains.intellij.build.io.file
import java.io.File
import java.io.Writer
import java.nio.channels.FileChannel
import java.nio.file.Path
import kotlin.let
import kotlin.text.isEmpty
import kotlin.text.substring

object JvmWorker {
  @JvmStatic
  fun main(startupArgs: Array<String>) {
    WorkRequestHandler(::handleRequest).processRequests(startupArgs)
  }
}

private fun handleRequest(workRequest: WorkerProtocol.WorkRequest, consoleOutput: Writer, baseDir: Path): Int {
  val args = workRequest.argumentsList
  if (args.isEmpty()) {
    consoleOutput.appendLine("Command is not specified")
    return 1
  }

  val command = args.first().split('|', limit = 5)
  @Suppress("SpellCheckingInspection")
  require(command.size > 2 && command[0] == "--flagfile=") {
    "Command format is incorrect: $command"
  }
  val taskKind = command[1]
  val output = command[2]
  when (taskKind) {
    "jar" -> {
      var stripPrefix = command[3]
      if (stripPrefix == "" && workRequest.inputsList.isNotEmpty()) {
        val p = workRequest.inputsList.first().path
        stripPrefix = command[4]
        var index = p.indexOf(stripPrefix)
        require(index != -1)
        stripPrefix = p.substring(0, index + stripPrefix.length)
      }
      createZip(
        outJar = Path.of(output),
        inputs = workRequest.inputsList,
        baseDir = baseDir,
        stripPrefix = stripPrefix,
      )

      return 0
    }
    "jdeps" -> {
      val inputs = workRequest.inputsList.asSequence()
        .filter { it.path.endsWith(".jdeps") }
        .map { baseDir.resolve(it.path) }
      //Files.writeString(Path.of("${System.getProperty("user.home")}/f.txt"), inputs.joinToString("\n") { it.toString() })
      mergeJdeps(
        consoleOutput = consoleOutput,
        label = command[3],
        output = Path.of(output),
        reportUnusedDeps = command[4],
        inputs = inputs,
      )
      return 0
    }
    else -> {
      consoleOutput.appendLine("Command is not supported: $taskKind (command=$command)")
      return 1
    }
  }
}

private fun createZip(outJar: Path, inputs: List<WorkerProtocol.Input>, baseDir: Path, stripPrefix: String) {
  //Files.writeString(Path.of("${System.getProperty("user.home")}/f.txt"), stripPrefix + "\n" + inputs.joinToString("\n") { it.toString() })

  val stripPrefixWithSlash = stripPrefix.let { if (it.isEmpty()) "" else "$it/" }
  val files = ArrayList<String>(inputs.size)
  for (input in inputs) {
    var p = input.path
    if (!p.startsWith(stripPrefixWithSlash)) {
      // input can contain jdeps/our jar in the end
      continue
    }

    files.add(p.substring(stripPrefixWithSlash.length))
  }

  files.sort()
  val root = baseDir.resolve(stripPrefix)

  //Files.writeString(Path.of("/tmp/f2.txt"), stripPrefixWithSlash + "\n" + files.joinToString("\n") { it.toString() })

  val packageIndexBuilder = PackageIndexBuilder()
  ZipArchiveOutputStream(
    channel = FileChannel.open(outJar, W_OVERWRITE),
    zipIndexWriter = ZipIndexWriter(packageIndexBuilder.indexWriter)
  ).use { stream ->
    for (path in files) {
      val name = path.replace(File.separatorChar, '/')
      packageIndexBuilder.addFile(name = name, addClassDir = false)
      stream.file(nameString = name, file = root.resolve(path))
    }
    packageIndexBuilder.writePackageIndex(stream = stream, addDirEntriesMode = AddDirEntriesMode.RESOURCE_ONLY)
  }
}