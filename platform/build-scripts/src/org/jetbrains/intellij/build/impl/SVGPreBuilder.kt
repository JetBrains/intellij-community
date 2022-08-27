// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ForkJoinTask

/*
Please do not convert this to direct call of ImageSvgPreCompiler
since it brings too much modules to build scripts classpath, and
it'll be too slow to compile by jps-bootstrap

Please do not call it via constructing special classloader, since
external process does not take too much time, and it's much easier to reason about
*/
object SVGPreBuilder {
  fun createPrebuildSvgIconsTask(context: BuildContext): ForkJoinTask<*>? {
    return createSkippableTask(spanBuilder("prebuild SVG icons"), BuildOptions.SVGICONS_PREBUILD_STEP, context) {
      val requestBuilder = StringBuilder()
      // build for all modules - so, icon db will be suitable for any non-bundled plugin
      for (module in context.project.modules) {
        requestBuilder.append(context.getModuleOutputDir(module).toString()).append("\n")
      }

      val requestFile = context.paths.tempDir.resolve("svg-prebuild-request.txt")
      Files.createDirectories(requestFile.parent)
      Files.writeString(requestFile, requestBuilder)
      val svgToolClasspath = context.getModuleRuntimeClasspath(module = context.findRequiredModule("intellij.platform.images.build"),
                                                               forTests = false)
      runSvgTool(context = context, svgToolClasspath = svgToolClasspath, requestFile = requestFile)
    }
  }

  private fun runSvgTool(context: BuildContext, svgToolClasspath: List<String>, requestFile: Path) {
    val dbDir = context.paths.tempDir.resolve("icons.db.dir")
    runJava(
      context = context,
      mainClass = "org.jetbrains.intellij.build.images.ImageSvgPreCompiler",
      args = listOf(dbDir.toString(), requestFile.toString()) + context.applicationInfo.svgProductIcons,
      jvmArgs = listOf("-Xmx1024m"),
      classPath = svgToolClasspath
    )

    Files.newDirectoryStream(dbDir).use { dirStream ->
      var found = false
      for (file in dirStream) {
        if (!Files.isRegularFile(file)) {
          context.messages.error("SVG tool: output must be a regular file: $file")
        }

        found = true
        context.addDistFile(java.util.Map.entry(file, "bin/icons"))
      }

      if (!found) {
        context.messages.error("SVG tool: after running SVG prebuild it must be a least one file at $dbDir")
      }
    }
  }
}