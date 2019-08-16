// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import groovy.io.FileType
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.jps.model.module.JpsModule

@CompileStatic
class SVGPreBuilder {
  static final List<String> getModulesToInclude() {
    return ["intellij.platform.images.build"]
  }

  private static File getResultDir(BuildContext buildContext) {
    new File(buildContext.paths.temp, "svg-prebuild/result")
  }

  static void prebuildSVGIcons(BuildContext buildContext, List<String> modules) {
    Set<String> modulesToProcess = new TreeSet<>(modules)
    buildContext.executeStep("Prebuild SVG icons", BuildOptions.SVGICONS_PREBUILD_STEP, {
      buildContext.messages.progress("Prebuild SVG icons for ${modulesToProcess.size()} modules")
      buildContext.messages.debug("Prebuild SVG icons are going to be built for the following modules: $modulesToProcess")

      File resultDir = getResultDir(buildContext)
      resultDir.mkdirs()
      File requestFile = new File(resultDir, "request.txt")

      requestFile.withPrintWriter("UTF-8") { writer ->
        for (String moduleName : modulesToProcess) {
          JpsModule module = buildContext.findRequiredModule(moduleName)
          File outputFile = new File(buildContext.getModuleOutputPath(module))
          File resultFile = new File(resultDir, moduleName)

          writer.println(outputFile.absolutePath)
          writer.println(resultFile.absolutePath)
        }
      }

      JpsModule buildModule = buildContext.findModule("intellij.platform.images.build")
      List<String> svgToolClasspath = buildContext.getModuleRuntimeClasspath(buildModule, false)
      runSVGTool(buildContext, svgToolClasspath, requestFile)
    })
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private static void runSVGTool(BuildContext buildContext, List<String> svgToolClasspath, File requestFile) {
    buildContext.ant.java(classname: "org.jetbrains.intellij.build.images.ImageSvgPreCompiler", fork: true, failonerror: true) {
      jvmarg(line: "-ea -Xmx500m")
      sysproperty(key: "java.awt.headless", value: true)
      arg(path: requestFile.absolutePath)

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

  static void addGeneratedResources(BuildContext buildContext, LayoutBuilder layoutBuilder) {
    if (!buildContext.options.buildStepsToSkip.contains(BuildOptions.SVGICONS_PREBUILD_STEP)) {
      File resultDir = getResultDir(buildContext)
      if (!resultDir.isDirectory()) {
        buildContext.messages.error("There are no SVG prebuilt images generated. " +
                                    "Please ensure that you call org.jetbrains.intellij.build.impl.SVGPreBuilder.prebuildSVGIcons before this method.")
      }
      resultDir.eachFile(FileType.DIRECTORIES) {
        layoutBuilder.patchModuleOutput(it.name, FileUtil.toSystemIndependentName(it.absolutePath))
      }
    }
  }
}
