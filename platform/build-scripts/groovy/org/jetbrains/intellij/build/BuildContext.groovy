// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import io.opentelemetry.api.trace.SpanBuilder
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.DependenciesProperties
import org.jetbrains.jps.model.module.JpsModule

import java.nio.file.Path
import java.util.function.UnaryOperator

@CompileStatic
abstract class BuildContext implements CompilationContext {
  ProductProperties productProperties
  WindowsDistributionCustomizer windowsDistributionCustomizer
  LinuxDistributionCustomizer linuxDistributionCustomizer
  MacDistributionCustomizer macDistributionCustomizer
  ProprietaryBuildTools proprietaryBuildTools
  DependenciesProperties dependenciesProperties

  abstract ApplicationInfoProperties getApplicationInfo()

  /**
   * Build number without product code (e.g. '162.500.10')
   */
  String buildNumber

  /**
   * Build number with product code (e.g. 'IC-162.500.10')
   */
  String fullBuildNumber

  /**
   * An identifier which will be used to form names for directories where configuration and caches will be stored, usually a product name
   * without spaces with added version ('IntelliJIdea2016.1' for IntelliJ IDEA 2016.1)
   */
  String systemSelector

  /**
   * Names of JARs inside `IDE_HOME/lib` directory which need to be added to the JVM boot classpath to start the IDE.
   */
  List<String> xBootClassPathJarNames

  /**
   * Names of JARs inside `IDE_HOME/lib` directory which need to be added to the JVM classpath to start the IDE.
   */
  List<String> bootClassPathJarNames

  /**
   * Allows customize classpath for buildSearchableOptions and builtinModules
   */
  UnaryOperator<Set<String>> classpathCustomizer

  /**
   * Add file to be copied into application.
   */
  abstract void addDistFile(@NotNull Map.Entry<Path, String> file)

  abstract @NotNull Collection<Map.Entry<Path, String>> getDistFiles()

  abstract boolean includeBreakGenLibraries()

  abstract void patchInspectScript(@NotNull Path path)

  /**
   * Unlike VM options produced by {@link org.jetbrains.intellij.build.impl.VmOptionsGenerator},
   * these are hard-coded into launchers and aren't supposed to be changed by a user.
   */
  abstract @NotNull List<String> getAdditionalJvmArguments()

  abstract void notifyArtifactBuilt(Path artifactPath)

  abstract JpsModule findApplicationInfoModule()

  abstract @Nullable Path findFileInModuleSources(@NotNull String moduleName, @NotNull String relativePath)

  void signFile(@NotNull Path file, Map<String, String> options = Collections.emptyMap()) {
    signFiles(List.of(file), options)
  }

  abstract void signFiles(@NotNull List<Path> files, Map<String, String> options = Collections.emptyMap())

  /**
   * Execute a build step or skip it if {@code stepId} is included into {@link BuildOptions#buildStepsToSkip}
   * @return {@code true} if the step was executed
   */
  abstract boolean executeStep(String stepMessage, String stepId, Runnable step)

  abstract void executeStep(SpanBuilder spanBuilder, String stepId, Runnable step)

  abstract boolean shouldBuildDistributions()

  abstract boolean shouldBuildDistributionForOS(String os)

  static BuildContext createContext(String communityHome, String projectHome, ProductProperties productProperties,
                                    ProprietaryBuildTools proprietaryBuildTools = ProprietaryBuildTools.DUMMY,
                                    BuildOptions options = new BuildOptions()) {
    return BuildContextImpl.create(communityHome, projectHome, productProperties, proprietaryBuildTools, options)
  }

  /**
   * Creates copy of this context which can be used to start a parallel task.
   * @param taskName short name of the task. It will be prepended to the messages from that task to distinguish them from messages from
   * other tasks running in parallel
   */
  abstract BuildContext forkForParallelTask(String taskName)

  abstract BuildContext createCopyForProduct(ProductProperties productProperties, String projectHomeForCustomizers)
}
