// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package org.jetbrains.intellij.build.tasks

import com.intellij.util.lang.ImmutableZipFile
import org.jetbrains.intellij.build.io.ZipArchiver
import org.jetbrains.intellij.build.io.deleteDir
import org.jetbrains.intellij.build.io.runJavaWithOutputToFile
import org.jetbrains.intellij.build.io.writeNewZip
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.List
import java.util.concurrent.ForkJoinTask
import java.util.function.Consumer

@Suppress("unused")
fun runScrambler(scramblerJar: Path,
                 mainClass: String,
                 scriptFile: Path,
                 pluginDir: Path?,
                 workingDir: Path,
                 artifactDir: Path,
                 files: List<Path>,
                 args: Iterable<String>,
                 jvmArgs: Iterable<String>,
                 artifactBuilt: Consumer<Path>) {
  val logSpecifier = pluginDir?.fileName?.toString() ?: "main"
  val logDir = artifactDir.resolve("scramble-logs")
  val processOutputFile = logDir.resolve("$logSpecifier-process-output.log")
  try {
    //noinspection SpellCheckingInspection
    runJavaWithOutputToFile(
      mainClass = mainClass,
      args = args,
      jvmArgs = jvmArgs,
      classPath = List.of(scramblerJar.toString()),
      workingDir = workingDir,
      outputFile = processOutputFile
    )

    if (pluginDir == null) {
      // yes, checked only for a main JAR
      checkClassFilesValidity(files.first())

      // update package index
      ForkJoinTask.invokeAll(files.map { file ->
        task(tracer.spanBuilder("update package index after scrambling")
               .setAttribute("file", file.toString())) {
          updatePackageIndexUsingTempFile(file)
        }
      })
    }
    else {
      Files.walk(pluginDir).use { walk ->
        walk
          .filter { it.fileName.toString().endsWith(".BACKUP") }
          .forEach { Files.delete(it) }
      }

      ForkJoinTask.invokeAll(files.map { file ->
        task(tracer.spanBuilder("update package index after scrambling")
               .setAttribute("plugin", pluginDir.toString())
               .setAttribute("file", pluginDir.relativize(file).toString())) {
          updatePackageIndexUsingTempFile(file)
        }
      })
    }
  }
  finally {
    val zippedLogsFile = logDir.resolve("$logSpecifier.zip")
    writeNewZip(zippedLogsFile, compress = true, hintIsSmall = true) { zipCreator ->
      val archiver = ZipArchiver(zipCreator)
      archiver.setRootDir(workingDir, "")
      Files.newDirectoryStream(workingDir).use { dirStream ->
        for (file in dirStream) {
          if (file.toString().endsWith(".txt")) {
            archiver.addFile(file)
            Files.delete(file)
          }
        }
      }

      archiver.addFile(scriptFile)
    }

    Files.delete(scriptFile)
    if (pluginDir == null) {
      deleteDir(workingDir)
    }

    artifactBuilt.accept(processOutputFile)
    artifactBuilt.accept(zippedLogsFile)
  }
}

private fun updatePackageIndexUsingTempFile(file: Path) {
  val tempFile = file.parent.resolve("${file.fileName}.tmp")
  try {
    updatePackageIndex(file, tempFile)
    Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING)
  }
  finally {
    Files.deleteIfExists(tempFile)
  }
}

// Scramble tool may produce invalid class files (see https://youtrack.jetbrains.com/issue/IDEA-188497)
// so we check validity of the produced class files here
private fun checkClassFilesValidity(jarFile: Path) {
  tracer.spanBuilder("check class files validity").setAttribute("file", jarFile.toString()).startSpan().use {
    ImmutableZipFile.load(jarFile).use { file ->
      for (entry in file.entries) {
        if (!entry.isDirectory && entry.name.endsWith(".class")) {
          entry.getInputStream(file).use {
            try {
              ClassReader(it).accept(object : ClassVisitor(Opcodes.API_VERSION) {}, 0)
            }
            catch (e: Throwable) {
              throw RuntimeException("Scrambler produced invalid class-file ${entry.name}", e)
            }
          }
        }
      }
    }
  }
}

