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
    return Arrays.asList("intellij.platform.images.build")
  }

  private static File getResultsDir(BuildContext buildContext) {
    new File(buildContext.paths.temp, "svg-prebuild/result")
  }

  static void prebuildSVGIcons(BuildContext buildContext, List<String> modules) {
    Set<String> modulesToProcess = new TreeSet<>(modules)
    buildContext.executeStep("Prebuild SVG icons", BuildOptions.SVGICONS_PREBUILD_STEP, {
      buildContext.messages.progress("Prebuild SVG icons for ${modulesToProcess.size()} modules")
      buildContext.messages.debug("Prebuild SVG icons are going to be built for the following modules: $modulesToProcess")

      File resultDir = getResultsDir(buildContext)
      resultDir.mkdirs()

      StringBuilder requestText = new StringBuilder()
      for (String moduleName : modulesToProcess) {
        def module = buildContext.findModule(moduleName)
        def outputPath = buildContext.getModuleOutputPath(module)
        def resultPath = new File(resultDir, moduleName)

        requestText.append(outputPath)
        requestText.append("\n")
        requestText.append(resultPath)
        requestText.append("\n")
      }

      File requestFile = new File(resultDir, "request.txt")
      requestFile.setText(requestText.toString(), "UTF-8")

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
      if (buildContext.productProperties.platformPrefix != null) {
        sysproperty(key: "idea.platform.prefix", value: buildContext.productProperties.platformPrefix)
      }
      arg(path: "$requestFile")

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
      File searchableOptionsDir = getResultsDir(buildContext)
      if (!searchableOptionsDir.isDirectory()) {
        buildContext.messages.error("There are no SVG prebuilt images generated. " +
                                    "Please ensure that you call org.jetbrains.intellij.build.impl.SVGPreBuilder.prebuildSVGIcons before this method.")
      }
      searchableOptionsDir.eachFile(FileType.DIRECTORIES) {
        layoutBuilder.patchModuleOutput(it.name, FileUtil.toSystemIndependentName(it.absolutePath))
      }
    }
  }
}
