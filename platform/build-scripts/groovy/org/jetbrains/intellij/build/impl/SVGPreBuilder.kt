// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.impl.TracerManager.spanBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.Map
import java.util.concurrent.ForkJoinTask
import java.util.stream.Collector
import java.util.stream.Collectors

object SVGPreBuilder {
  fun createPrebuildSvgIconsTask(context: BuildContext): ForkJoinTask<*>? {
    // Note ****************************
    // Please do not convert this to direct call of ImageSvgPreCompiler
    // since it brings too much modules to build scripts classpath and
    // it'll be too slow to compile by jps-bootstrap
    //
    // Please do not call it via constructing special classloader, since
    // external process does not take too much time and it's much easier to reason about
    // Note ****************************
    return BuildHelper.createSkippableTask(spanBuilder("prebuild SVG icons"), BuildOptions.SVGICONS_PREBUILD_STEP, context
    ) {
      val requestFile = context.paths.tempDir.resolve("svg-prebuild-request.txt")
      val requestBuilder = StringBuilder()
      // build for all modules - so, icon db will be suitable for any non-bundled plugin
      for (module in context.project.modules) {
        requestBuilder.append(context.getModuleOutputDir(
          module!!).toString()).append("\n")
      }
      Files.createDirectories(requestFile.parent)
      Files.writeString(requestFile, requestBuilder)
      val buildModule = context.findModule("intellij.platform.images.build")
      val svgToolClasspath = context.getModuleRuntimeClasspath(buildModule, false)
      runSVGTool(context, svgToolClasspath, requestFile)
    }
  }

  private fun runSVGTool(buildContext: BuildContext, svgToolClasspath: List<String>, requestFile: Path) {
    val dbDir = buildContext.paths.tempDir.resolve("icons.db.dir")
    BuildHelper.runJava(buildContext, "org.jetbrains.intellij.build.images.ImageSvgPreCompiler",
                        DefaultGroovyMethods.plus(ArrayList(Arrays.asList(dbDir.toString(), requestFile.toString())),
                                                  buildContext.getApplicationInfo().svgProductIcons), java.util.List.of("-Xmx1024m"),
                        svgToolClasspath)
    val filesToPublish: List<Path> = Files.list(dbDir).collect(Collectors.toList<Any?>() as Collector<in Path?, *, List<Path?>?>)
    if (filesToPublish.size <= 1) {
      buildContext.messages.error("SVG tool: after running SVG prebuild it must be a least one file at $dbDir")
    }
    for (file in filesToPublish) {
      if (!Files.isRegularFile(file)) {
        buildContext.messages.error("SVG tool: output must be a regular file: $file")
      }
      buildContext.addDistFile(Map.entry(file, "bin/icons"))
    }
  }
}