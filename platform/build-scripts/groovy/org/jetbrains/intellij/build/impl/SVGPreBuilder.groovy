// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.Pair
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.jps.model.module.JpsModule

import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer

@CompileStatic
final class SVGPreBuilder {
  static BuildTaskRunnable<Void> createPrebuildSvgIconsTask() {
    return BuildTaskRunnable.task(BuildOptions.SVGICONS_PREBUILD_STEP, "Prebuild SVG icons", new Consumer<BuildContext>() {
      @Override
      void accept(BuildContext buildContext) {
        Path requestFile = buildContext.paths.tempDir.resolve("svg-prebuild-request.txt")

        StringBuilder requestBuilder = new StringBuilder()
        // build for all modules - so, icon db will be suitable for any non-bundled plugin
        for (JpsModule module : buildContext.getProject().getModules()) {
          requestBuilder.append(buildContext.getModuleOutputPath(module)).append('\n')
        }
        Files.createDirectories(requestFile.getParent())
        Files.writeString(requestFile, requestBuilder)

        JpsModule buildModule = buildContext.findModule("intellij.platform.images.build")
        List<String> svgToolClasspath = buildContext.getModuleRuntimeClasspath(buildModule, false)
        runSVGTool(buildContext, svgToolClasspath, requestFile)
      }
    })
  }

  private static void runSVGTool(BuildContext buildContext, List<String> svgToolClasspath, Path requestFile) {
    Path dbFile = buildContext.paths.tempDir.resolve("icons.db")
    BuildHelper.runJava(buildContext,
                        "org.jetbrains.intellij.build.images.ImageSvgPreCompiler",
                        [dbFile.toString(), requestFile.toString()] + buildContext.applicationInfo.svgProductIcons,
                        List.of("-Xmx1024m"),
                        svgToolClasspath)
    buildContext.addDistFile(new Pair<Path, String>(dbFile, "bin"))
  }
}
