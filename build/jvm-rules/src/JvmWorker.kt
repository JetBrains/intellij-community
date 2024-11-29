// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm

import com.google.devtools.build.lib.worker.WorkerProtocol
import org.jetbrains.intellij.build.io.AddDirEntriesMode
import org.jetbrains.intellij.build.io.PackageIndexBuilder
import org.jetbrains.intellij.build.io.W_OVERWRITE
import org.jetbrains.intellij.build.io.ZipFileWriter
import org.jetbrains.intellij.build.io.ZipIndexWriter
import java.io.File
import java.io.Writer
import java.nio.channels.FileChannel
import java.nio.file.Path
import kotlin.let
import kotlin.system.exitProcess
import kotlin.text.isEmpty
import kotlin.text.substring

private val workingDir = Path.of(".").toAbsolutePath().normalize()

object JvmWorker {
  @JvmStatic
  fun main(args: Array<String>) {
    if (!args.contains("--persistent_worker")) {
      System.err.println("Only persistent worker mode is supported")
      exitProcess(1)
    }

    WorkRequestHandler(::handleRequest).processRequests()
  }
}

private fun handleRequest(workRequest: WorkerProtocol.WorkRequest, output: Writer): Int {
  val args = workRequest.argumentsList
  if (args.isEmpty()) {
    output.appendLine("Command is not specified")
    return 1
  }

  val command = args.first().split('|', limit = 4)
  @Suppress("SpellCheckingInspection")
  require(command.size > 2 && command[0] == "--flagfile=") {
    "Command format is incorrect: $command"
  }
  val taskKind = command[1]
  if (taskKind != "jar") {
    output.appendLine("Command is not supported: $taskKind (command=$command)")
    return 1
  }

  val output = command[2]
  var baseDir = workingDir
  if (!workRequest.sandboxDir.isNullOrEmpty()) {
    baseDir = baseDir.resolve(workRequest.sandboxDir)
  }
  createZip(
    outJar = Path.of(output),
    inputs = workRequest.inputsList,
    baseDir = baseDir,
    stripPrefix = command[3],
  )

  return 0
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
  // withCrc = false doesn't work correctly yet
  ZipFileWriter(
    channel = FileChannel.open(outJar, W_OVERWRITE),
    zipIndexWriter = ZipIndexWriter(indexWriter = packageIndexBuilder.indexWriter),
    withCrc = true,
  ).use { zipFileWriter ->
    for (path in files) {
      val name = path.replace(File.separatorChar, '/')
      packageIndexBuilder.addFile(name = name, addClassDir = false)
      zipFileWriter.file(nameString = name, file = root.resolve(path))
    }
    packageIndexBuilder.writePackageIndex(zipCreator = zipFileWriter, addDirEntriesMode = AddDirEntriesMode.RESOURCE_ONLY)
  }
}