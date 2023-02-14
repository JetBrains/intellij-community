// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.DistFile
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import java.nio.file.Files
import java.nio.file.Path

/*
Please do not convert this to a direct call of ImageSvgPreCompiler
since it brings too many modules to build scripts classpath, and it'll be too slow to compile by jps-bootstrap

Please do not call it by constructing a special classloader, since
an external process does not take too much time, and it's much easier to reason about.
*/
internal fun CoroutineScope.createPrebuildSvgIconsJob(context: BuildContext): Job? {
  return createSkippableJob(spanBuilder("prebuild SVG icons"), BuildOptions.SVGICONS_PREBUILD_STEP, context) {
    val requestBuilder = StringBuilder()
    // build for all modules - so, icon db will be suitable for any non-bundled plugin
    for (module in context.project.modules) {
      requestBuilder.append(context.getModuleOutputDir(module).toString()).append('\n')
    }

    val requestFile = context.paths.tempDir.resolve("svg-prebuild-request.txt")
    withContext(Dispatchers.IO) {
      Files.createDirectories(requestFile.parent)
      Files.writeString(requestFile, requestBuilder)
      val svgToolClasspath = context.getModuleRuntimeClasspath(module = context.findRequiredModule("intellij.platform.images.build"),
                                                               forTests = false)
      runSvgTool(context = context, svgToolClasspath = svgToolClasspath, requestFile = requestFile)
    }
  }
}

/**
 * Concurrent [org.jetbrains.skiko.Library.unpackIfNeeded] may throw [java.nio.file.AccessDeniedException]
 */
private val imageSvgPreCompilerLock = Mutex()

private suspend fun runSvgTool(context: BuildContext, svgToolClasspath: List<String>, requestFile: Path) {
  val dbDir = context.paths.tempDir.resolve("icon-db")
  imageSvgPreCompilerLock.withLock {
    runIdea(
      context = context,
      mainClass = "org.jetbrains.intellij.build.images.ImageSvgPreCompiler",
      args = listOf(dbDir.toString(), requestFile.toString()) + context.applicationInfo.svgProductIcons,
      jvmArgs = listOf(if (Runtime.getRuntime().availableProcessors() < 10) "-Xmx1024m" else "-Xmx2048m", "-Djava.awt.headless=true"),
      classPath = svgToolClasspath
    )
  }

  Files.newDirectoryStream(dbDir).use { dirStream ->
    var found = false
    for (file in dirStream) {
      check(Files.isRegularFile(file)) {
        "SVG tool: output must be a regular file: $file"
      }

      found = true
      context.addDistFile(DistFile(file = file, relativePath = "bin/icons/${file.fileName}"))
    }

    check(found) {
      "SVG tool: after running SVG prebuild it must be a least one file at $dbDir"
    }
  }
}
