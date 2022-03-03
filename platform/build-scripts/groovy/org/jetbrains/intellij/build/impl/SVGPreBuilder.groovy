// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.jps.model.module.JpsModule

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Path
import java.util.concurrent.ForkJoinTask

@CompileStatic
final class SVGPreBuilder {
  @Nullable
  static ForkJoinTask<?> createPrebuildSvgIconsTask(@NotNull BuildContext context) {
    return BuildHelper.getInstance(context).createSkippableTask(
      TracerManager.spanBuilder("prebuild SVG icons"),
      BuildOptions.SVGICONS_PREBUILD_STEP,
      context,
      new Runnable() {
        @Override
        void run() {
          runSvgTool(context)
        }
      }
    )
  }

  private static void runSvgTool(@NotNull BuildContext context) {
    List<Path> moduleOutputs = new ArrayList<>()
    // build for all modules - so, icon db will be suitable for any non-bundled plugin
    for (JpsModule module : context.getProject().getModules()) {
      moduleOutputs.add(context.getModuleOutputDir(module))
    }

    List<Path> classPathFiles = BuildHelper.buildClasspathForModule(context.findRequiredModule("intellij.platform.images.build"), context)
    // don't use index - avoid saving to output (reproducible builds)
    ClassLoader classLoader = BuildHelper.createClassLoader(classPathFiles)
    MethodHandle handle = MethodHandles.lookup().findStatic(classLoader.loadClass("org.jetbrains.intellij.build.images.ImageSvgPreCompiler"),
                                                            "optimize",
                                                            MethodType.methodType(List.class,
                                                                                  Path.class, Path.class, List.class))
    Path dbDir = context.paths.tempDir.resolve("icons")
    List<Path> files = (List<Path>)handle.invokeWithArguments(dbDir, context.getProjectOutputDirectory().toPath().resolve("production"),
                                                              moduleOutputs)
    for (Path file : files) {
      context.addDistFile(Map.entry(file, "bin/icons"))
    }
  }
}
