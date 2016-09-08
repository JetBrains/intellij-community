/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.BundledJreManager
import org.jetbrains.jps.gant.JpsGantProjectBuilder
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule

/**
 * @author nik
 */
@CompileStatic
abstract class BuildContext {
  AntBuilder ant
  BuildMessages messages
  BuildPaths paths
  JpsProject project
  ApplicationInfoProperties applicationInfo
  JpsGantProjectBuilder projectBuilder
  ProductProperties productProperties
  WindowsDistributionCustomizer windowsDistributionCustomizer
  LinuxDistributionCustomizer linuxDistributionCustomizer
  MacDistributionCustomizer macDistributionCustomizer
  ProprietaryBuildTools proprietaryBuildTools
  BuildOptions options
  BundledJreManager bundledJreManager

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

  abstract boolean includeBreakGenLibraries()

  /**
   * If the method returns {@code false} 'idea.jars.nocopy' property will be set to {@code true} in idea.properties by default. Otherwise it
   * won't be set and the IDE will copy library *.jar files to avoid their locking when running under Windows.
   */
  abstract boolean shouldIDECopyJarsByDefault()

  abstract void patchInspectScript(String path)

  abstract String getAdditionalJvmArguments()

  abstract void notifyArtifactBuilt(String artifactPath)

  abstract File findApplicationInfoInSources()

  abstract JpsModule findApplicationInfoModule()

  abstract JpsModule findRequiredModule(String name)

  abstract JpsModule findModule(String name)

  abstract File findFileInModuleSources(String moduleName, String relativePath)

  abstract void signExeFile(String path)

  /**
   * Execute a build step or skip it if {@code stepId} is included into {@link BuildOptions#buildStepsToSkip}
   */
  abstract void executeStep(String stepMessage, String stepId, Closure step)

  abstract boolean shouldBuildDistributionForOS(String os)

  static BuildContext createContext(AntBuilder ant, JpsGantProjectBuilder projectBuilder, JpsProject project, JpsGlobal global,
                                           String communityHome, String projectHome, ProductProperties productProperties,
                                           ProprietaryBuildTools proprietaryBuildTools = ProprietaryBuildTools.DUMMY,
                                           BuildOptions options = new BuildOptions()) {
    return BuildContextImpl.create(ant, projectBuilder, project, global, communityHome, projectHome, productProperties,
                                   proprietaryBuildTools, options)
  }

  /**
   * Creates copy of this context which can be used to start a parallel task.
   * @param taskName short name of the task. It will be prepended to the messages from that task to distinguish them from messages from
   * other tasks running in parallel
   */
  abstract BuildContext forkForParallelTask(String taskName)

  abstract BuildContext createCopyForProduct(ProductProperties productProperties, String projectHomeForCustomizers)
}

/**
 * All paths are absolute and use '/' as a separator
 */
abstract class BuildPaths {
  /**
   * Path to a directory where idea/community Git repository is checked out
   */
  String communityHome

  /**
   * Path to a base directory of the project which will be compiled
   */
  String projectHome

  /**
   * Path to a directory where build script will store temporary and resulting files
   */
  String buildOutputRoot

  /**
   * Path to a directory where resulting artifacts will be placed
   */
  String artifacts

  /**
   * Path to a directory containing distribution files ('bin', 'lib', 'plugins' directories) common for all operating systems
   */
  String distAll

  /**
   * Path to a directory where temporary files required for a particular build step can be stored
   */
  String temp

  /**
   * Path to a directory containing JDK (currently Java 8) which is used to compile the project
   */
  String jdkHome
}

interface BuildMessages {
  void info(String message)
  void warning(String message)

  /**
   * Report an error and stop the build process
   */
  void error(String message)

  void error(String message, Throwable cause)

  void progress(String message)
  def <V> V block(String blockName, Closure<V> body)

  void artifactBuild(String relativeArtifactPath)

  BuildMessages forkForParallelTask(String taskName)

  /**
   * Must be invoked from the main thread when all forks have been finished
   */
  void onAllForksFinished()

  /**
   * Must be invoked for the forked instance on the thread where it is executing before the task is started.
   * It's required to correctly handle messages from Ant tasks.
   */
  void onForkStarted()

  /**
   * Must be invoked for the forked instance on the thread where it is executing when the task is finished
   */
  void onForkFinished()
}
