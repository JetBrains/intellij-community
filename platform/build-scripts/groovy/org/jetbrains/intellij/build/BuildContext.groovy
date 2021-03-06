// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.Pair
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.BundledJreManager
import org.jetbrains.intellij.build.impl.DependenciesProperties
import org.jetbrains.jps.model.module.JpsModule

import java.nio.file.Path

@CompileStatic
abstract class BuildContext implements CompilationContext {
  ApplicationInfoProperties applicationInfo
  ProductProperties productProperties
  WindowsDistributionCustomizer windowsDistributionCustomizer
  LinuxDistributionCustomizer linuxDistributionCustomizer
  MacDistributionCustomizer macDistributionCustomizer
  ProprietaryBuildTools proprietaryBuildTools
  BundledJreManager bundledJreManager
  DependenciesProperties dependenciesProperties

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
   * Names of JARs inside IDE_HOME/lib directory which need to be added to bootclasspath to start the IDE
   */
  List<String> bootClassPathJarNames

  /**
   * Add file to be copied into application.
   */
  abstract void addDistFile(@NotNull Pair<Path, String> file)

  abstract @NotNull Collection<Pair<Path, String>> getDistFiles();

  abstract boolean includeBreakGenLibraries()

  abstract void patchInspectScript(@NotNull Path path)

  abstract String getAdditionalJvmArguments()

  abstract void notifyArtifactBuilt(String artifactPath)

  abstract void notifyArtifactBuilt(Path artifactPath)

  abstract JpsModule findApplicationInfoModule()

  abstract @Nullable Path findFileInModuleSources(String moduleName, String relativePath)

  abstract void signExeFile(String path)

  /**
   * Execute a build step or skip it if {@code stepId} is included into {@link BuildOptions#buildStepsToSkip}
   * @return {@code true} if the step was executed
   */
  abstract boolean executeStep(String stepMessage, String stepId, Runnable step)

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
