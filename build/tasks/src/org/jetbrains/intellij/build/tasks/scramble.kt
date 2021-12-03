// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package org.jetbrains.intellij.build.tasks

import org.jetbrains.intellij.build.io.*
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path
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
  val logSpecifier = pluginDir?.fileName?.toString() ?: files.first().fileName.toString().removeSuffix(".jar")
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

      // update package index (main jar will be merged into app.jar, so, skip it)
      ForkJoinTask.invokeAll(files.subList(1, files.size).map { file ->
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
    writeNewZip(zippedLogsFile, compress = true) { zipCreator ->
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
  transformFile(file) {
    updatePackageIndex(file, it)
  }
}

private fun updatePackageIndex(sourceFile: Path, targetFile: Path) {
  writeNewZip(targetFile) { zipCreator ->
    val packageIndexBuilder = PackageIndexBuilder()
    copyZipRaw(sourceFile, packageIndexBuilder, zipCreator)
    packageIndexBuilder.writePackageIndex(zipCreator)
  }
}

// Scramble tool may produce invalid class files (see https://youtrack.jetbrains.com/issue/IDEA-188497)
// so we check validity of the produced class files here
private fun checkClassFilesValidity(jarFile: Path) {
  val checkVisitor = object : ClassVisitor(Opcodes.API_VERSION) {}
  tracer.spanBuilder("check class files validity").setAttribute("file", jarFile.toString()).startSpan().use {
    readZipFile(jarFile) { name, entry ->
      if (name.endsWith(".class")) {
        try {
          ClassReader(entry.getData()).accept(checkVisitor, ClassReader.SKIP_DEBUG)
        }
        catch (e: Throwable) {
          throw RuntimeException("Scrambler produced invalid class-file $name", e)
        }
      }
    }
  }
}

