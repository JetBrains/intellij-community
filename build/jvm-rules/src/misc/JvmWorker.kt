// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm

import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.io.AddDirEntriesMode
import org.jetbrains.intellij.build.io.PackageIndexBuilder
import org.jetbrains.intellij.build.io.zipWriter
import java.io.File
import java.io.Writer
import java.nio.file.Path

object JvmWorker : WorkRequestExecutor {
  @JvmStatic
  fun main(startupArgs: Array<String>) {
    processRequests(
      startupArgs = startupArgs,
      executorFactory = { _, _ -> this },
      reader = WorkRequestReaderWithoutDigest(System.`in`),
      serviceName = "jvm-worker",
    )
  }

  override suspend fun execute(request: WorkRequest, writer: Writer, baseDir: Path, tracer: Tracer): Int {
    val args = request.arguments
    if (args.isEmpty()) {
      writer.appendLine("Command is not specified")
      return 1
    }

    val command = args.first().split('|', limit = 6)
    @Suppress("SpellCheckingInspection")
    require(command.size > 2 && command[0] == "--flagfile=") {
      "Command format is incorrect: $command"
    }
    val taskKind = command[1]
    val output = command[2]
    when (taskKind) {
      "jar" -> {
        val addPrefix = command[3]
        var stripPrefix = command[4]
        if (stripPrefix == "" && request.inputs.isNotEmpty()) {
          val p = request.inputs.first().path
          stripPrefix = command[5]
          val index = p.indexOf(stripPrefix)
          require(index != -1)
          stripPrefix = p.take(index + stripPrefix.length)
        }
        createZip(
          outJar = baseDir.resolve(output),
          inputs = request.inputs,
          baseDir = baseDir,
          addPrefix = addPrefix,
          stripPrefix = stripPrefix,
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

private suspend fun createZip(outJar: Path, inputs: Array<Input>, baseDir: Path, addPrefix: String, stripPrefix: String) {
  //Files.writeString(Path.of("${System.getProperty("user.home")}/f.txt"), stripPrefix + "\n" + inputs.joinToString("\n") { it.toString() })

  require(!addPrefix.endsWith('/')) {
    "addPrefix must not end with '/': $addPrefix"
  }
  val addPrefixWithSlash = addPrefix.let { if (it.isEmpty()) "" else "$it/" }

  val stripPrefixWithSlash = stripPrefix.let { if (it.isEmpty()) "" else "$it/" }
  val files = ArrayList<String>(inputs.size)
  for (input in inputs) {
    if (!input.path.startsWith(stripPrefixWithSlash)) {
      // input can contain our jar in the end
      continue
    }

    files.add(input.path.substring(stripPrefixWithSlash.length).replace(File.separatorChar, '/'))
  }

  files.sort()
  val root = baseDir.resolve(stripPrefix)

  //Files.writeString(Path.of("/tmp/f2.txt"), stripPrefixWithSlash + "\n" + files.joinToString("\n") { it.toString() })

  withContext(Dispatchers.IO) {
    val packageIndexBuilder = PackageIndexBuilder(AddDirEntriesMode.RESOURCE_ONLY)
    zipWriter(targetFile = outJar, packageIndexBuilder = packageIndexBuilder, overwrite = true).use { stream ->
      for (path in files) {
        val name = addPrefixWithSlash + path
        packageIndexBuilder.addFile(name = name)
        stream.fileWithoutCrc(path = name.toByteArray(), file = root.resolve(path))
      }
    }
  }
}