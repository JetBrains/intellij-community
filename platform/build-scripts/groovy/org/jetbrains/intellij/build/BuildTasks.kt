// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build;

import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectStructureMapping;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

public abstract class BuildTasks {
  /**
   * Builds archive containing production source roots of the project modules. If {@code includeLibraries} is {@code true}, the produced
   * archive also includes sources of project-level libraries on which platform API modules from {@code modules} list depend on.
   */
  public abstract void zipSourcesOfModules(Collection<String> modules, Path targetFile, boolean includeLibraries);

  public void zipSourcesOfModules(Collection<String> modules, Path targetFile) {
    zipSourcesOfModules(modules, targetFile, false);
  }

  public void zipSourcesOfModules(Collection<String> modules, String targetFilePath) {
    zipSourcesOfModules(modules, Path.of(targetFilePath));
  }

  /**
   * Produces distributions for all operating systems from sources. This includes compiling required modules, packing their output into JAR
   * files accordingly to {@link ProductProperties#productLayout}, and creating distributions and installers for all OS.
   */
  public abstract void buildDistributions();

  public abstract void compileModulesFromProduct();

  /**
   * Compiles required modules and builds zip archives of the specified plugins in {@link BuildPaths#artifactDir artifacts}/&lt;product-code&gt;-plugins
   * directory.
   */
  public abstract void buildNonBundledPlugins(List<String> mainPluginModules);

  /**
   * Generates a JSON file containing mapping between files in the product distribution and modules and libraries in the project configuration
   *
   * @see ProjectStructureMapping
   */
  public abstract void generateProjectStructureMapping(@NotNull Path targetFile);

  public abstract void compileProjectAndTests(List<String> includingTestsInModules);

  public abstract void compileModules(@Nullable Collection<String> moduleNames, List<String> includingTestsInModules);

  public void compileModules(@Nullable Collection<String> moduleNames) {
    compileModules(moduleNames, List.of());
  }

  public abstract void buildUpdaterJar();

  /**
   * Builds updater-full.jar artifact which includes 'intellij.platform.updater' module with all its dependencies
   */
  public abstract void buildFullUpdaterJar();

  /**
   * Performs a fast dry run to check that the build scripts a properly configured. It'll run compilation, build platform and plugin JAR files,
   * build searchable options index and scramble the main JAR, but won't produce the product archives and installation files which occupy a lot
   * of disk space and take a long time to build.
   */
  public abstract void runTestBuild();

  public abstract void buildUnpackedDistribution(@NotNull Path targetDirectory, boolean includeBinAndRuntime);

  public abstract void buildDmg(Path macZipDir);

  public static BuildTasks create(BuildContext context) {
    return DefaultGroovyMethods.asType(
      BuildTasks.class.getClassLoader().loadClass("org.jetbrains.intellij.build.impl.BuildTasksImpl").getConstructor(BuildContext.class)
        .newInstance(context), BuildTasks.class);
  }
}
