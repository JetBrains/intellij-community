// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.io.*
import java.io.File
import java.io.Writer
import java.nio.channels.FileChannel
import java.nio.file.Path

object JvmWorker : WorkRequestExecutor {
  @JvmStatic
  fun main(startupArgs: Array<String>) {
    processRequests(startupArgs, this)
  }

  override suspend fun execute(request: WorkRequest, writer: Writer, baseDir: Path): Int {
    val args = request.arguments
    if (args.isEmpty()) {
      writer.appendLine("Command is not specified")
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
        if (stripPrefix == "" && request.inputs.isNotEmpty()) {
          val p = request.inputs.first().path
          stripPrefix = command[4]
          val index = p.indexOf(stripPrefix)
          require(index != -1)
          stripPrefix = p.substring(0, index + stripPrefix.length)
        }
        createZip(
          outJar = Path.of(output),
          inputs = request.inputs,
          baseDir = baseDir,
          stripPrefix = stripPrefix,
        )

        return 0
      }

      "jdeps" -> {
        val inputs = request.inputs.asSequence()
          .filter { it.path.endsWith(".jdeps") }
          .map { baseDir.resolve(it.path) }
        //Files.writeString(Path.of("${System.getProperty("user.home")}/f.txt"), inputs.joinToString("\n") { it.toString() })
        mergeJdeps(
          consoleOutput = writer,
          label = command[3],
          output = Path.of(output),
          reportUnusedDeps = command[4],
          inputs = inputs,
        )
        return 0
      }

      else -> {
        writer.appendLine("Command is not supported: $taskKind (command=$command)")
        return 1
      }
    }
  }
}

private suspend fun createZip(outJar: Path, inputs: Array<Input>, baseDir: Path, stripPrefix: String) {
  //Files.writeString(Path.of("${System.getProperty("user.home")}/f.txt"), stripPrefix + "\n" + inputs.joinToString("\n") { it.toString() })

  val stripPrefixWithSlash = stripPrefix.let { if (it.isEmpty()) "" else "$it/" }
  val files = ArrayList<String>(inputs.size)
  for (input in inputs) {
    val p = input.path
    if (!p.startsWith(stripPrefixWithSlash)) {
      // input can contain jdeps/our jar in the end
      continue
    }

    files.add(p.substring(stripPrefixWithSlash.length))
  }

  files.sort()
  val root = baseDir.resolve(stripPrefix)

  //Files.writeString(Path.of("/tmp/f2.txt"), stripPrefixWithSlash + "\n" + files.joinToString("\n") { it.toString() })

  withContext(Dispatchers.IO) {
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
}