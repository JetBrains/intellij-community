// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.jps.model.module.JpsModule

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths

@CompileStatic
class SVGPreBuilder {
  public static final String FILE_NAME = "icons.db"

  static final List<String> getModulesToInclude() {
    return ["intellij.platform.images.build"]
  }

  private static String getDbFile(BuildContext buildContext) {
    return "$buildContext.paths.temp/" + FILE_NAME
  }

  static copyIconDb(BuildContext buildContext, String newDirPath) {
    Path from = Paths.get(getDbFile(buildContext))
    Path newDir = Paths.get(newDirPath)
    Files.createDirectories(newDir)
    try {
      Files.copy(from, newDir.resolve(from.fileName))
    }
    catch (NoSuchFileException ignore) {
      // if for some reasons cache generation is failed or simply disabled, do not throw yet another error
    }
  }

  static void prebuildSVGIcons(BuildContext buildContext, List<String> modules) {
    buildContext.executeStep("Prebuild SVG icons", BuildOptions.SVGICONS_PREBUILD_STEP, {
      Set<String> modulesToProcess = new TreeSet<>(modules)
      buildContext.messages.progress("Prebuild SVG icons for ${modulesToProcess.size()} modules")
      buildContext.messages.debug("Prebuild SVG icons are going to be built for the following modules: $modulesToProcess")

      Path requestFile = Paths.get(buildContext.paths.temp, "svg-prebuild", "request.txt")

      StringBuilder requestBuilder = new StringBuilder()
      for (String moduleName : modulesToProcess) {
        requestBuilder.append(buildContext.getModuleOutputPath(buildContext.findRequiredModule(moduleName))).append('\n')
      }
      Files.createDirectories(requestFile.getParent())
      Files.writeString(requestFile, requestBuilder)

      JpsModule buildModule = buildContext.findModule("intellij.platform.images.build")
      List<String> svgToolClasspath = buildContext.getModuleRuntimeClasspath(buildModule, false)
      runSVGTool(buildContext, svgToolClasspath, requestFile)
    })
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private static void runSVGTool(BuildContext buildContext, List<String> svgToolClasspath, Path requestFile) {
    buildContext.ant.java(classname: "org.jetbrains.intellij.build.images.ImageSvgPreCompiler", fork: true, failonerror: true) {
      jvmarg(line: "-ea -Xmx500m")
      sysproperty(key: "java.awt.headless", value: true)
      arg(path: getDbFile(buildContext))
      arg(path: requestFile.toString())

      buildContext.applicationInfo.svgProductIcons.forEach {
        arg(value: "$it")
      }

      classpath() {
        for (String element : svgToolClasspath) {
          pathelement(location: "$element")
        }
      }
    }
  }
}
