// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl;

import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.intellij.build.BuildContext;
import org.jetbrains.intellij.build.BuildOptions;
import org.jetbrains.jps.model.module.JpsModule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinTask;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public final class SVGPreBuilder {
  public static ForkJoinTask<?> createPrebuildSvgIconsTask(@NotNull final BuildContext context) {
    // Note ****************************
    // Please do not convert this to direct call of ImageSvgPreCompiler
    // since it brings too much modules to build scripts classpath and
    // it'll be too slow to compile by jps-bootstrap
    //
    // Please do not call it via constructing special classloader, since
    // external process does not take too much time and it's much easier to reason about
    // Note ****************************

    return BuildHelper.createSkippableTask(TracerManager.spanBuilder("prebuild SVG icons"), BuildOptions.SVGICONS_PREBUILD_STEP, context,
                                           new Runnable() {
                                             @Override
                                             public void run() {
                                               Path requestFile = context.getPaths().getTempDir().resolve("svg-prebuild-request.txt");

                                               StringBuilder requestBuilder = new StringBuilder();
                                               // build for all modules - so, icon db will be suitable for any non-bundled plugin
                                               for (JpsModule module : context.getProject().getModules()) {
                                                 requestBuilder.append(context.getModuleOutputDir(module).toString()).append("\n");
                                               }

                                               Files.createDirectories(requestFile.getParent());
                                               Files.writeString(requestFile, requestBuilder);

                                               JpsModule buildModule = context.findModule("intellij.platform.images.build");
                                               List<String> svgToolClasspath = context.getModuleRuntimeClasspath(buildModule, false);
                                               runSVGTool(context, svgToolClasspath, requestFile);
                                             }
                                           });
  }

  private static void runSVGTool(BuildContext buildContext, List<String> svgToolClasspath, Path requestFile) {
    Path dbDir = buildContext.getPaths().getTempDir().resolve("icons.db.dir");
    BuildHelper.runJava(buildContext, "org.jetbrains.intellij.build.images.ImageSvgPreCompiler",
                        DefaultGroovyMethods.plus(new ArrayList<String>(Arrays.asList(dbDir.toString(), requestFile.toString())),
                                                  buildContext.getApplicationInfo().getSvgProductIcons()), List.of("-Xmx1024m"),
                        svgToolClasspath);

    List<Path> filesToPublish = Files.list(dbDir).collect((Collector<? super Path, ?, List<Path>>)Collectors.toList());

    if (filesToPublish.size() <= 1) {
      buildContext.getMessages().error("SVG tool: after running SVG prebuild it must be a least one file at " + String.valueOf(dbDir));
    }


    for (Path file : filesToPublish) {
      if (!Files.isRegularFile(file)) {
        buildContext.getMessages().error("SVG tool: output must be a regular file: " + String.valueOf(file));
      }


      buildContext.addDistFile(Map.entry(file, "bin/icons"));
    }
  }
}
