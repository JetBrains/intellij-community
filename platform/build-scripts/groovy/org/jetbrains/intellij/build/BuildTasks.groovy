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

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.codehaus.gant.GantBinding
import org.jetbrains.intellij.build.impl.BuildTasksImpl
import org.jetbrains.intellij.build.impl.BuildUtils
import org.jetbrains.jps.idea.IdeaProjectLoader

@CompileStatic
abstract class BuildTasks {
  /**
   * Builds sources.zip archive containing the project source files keeping the original layout
   */
  abstract void zipProjectSources()

  /**
   * Builds archive containing production source roots of the project modules
   */
  abstract void zipSourcesOfModules(Collection<String> modules, String targetFilePath)

  /**
   * Produces distributions for all operating systems from sources. This includes compiling required modules, packing their output into JAR
   * files accordingly to {@link ProductProperties#productLayout}, and creating distributions and installers for all OS.
   */
  abstract void buildDistributions()

  abstract void compileModulesFromProduct()

  /**
   * Compiles required modules and builds zip archives of the specified plugins in {@link BuildPaths#artifacts artifacts}/&lt;product-code&gt;-plugins
   * directory.
   */
  abstract void buildNonBundledPlugins(List<String> mainPluginModules)

  /**
   * Generates a JSON file containing mapping between files in the product distribution and modules and libraries in the project configuration
   * @see org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectStructureMapping
   */
  abstract void generateProjectStructureMapping(File targetFile)

  abstract void compileProjectAndTests(List<String> includingTestsInModules)

  abstract void compileModules(List<String> moduleNames, List<String> includingTestsInModules = [])

  abstract void buildUpdaterJar()

  /**
   * Builds updater-full.jar artifact which includes 'intellij.platform.updater' module with all its dependencies
   */
  abstract void buildFullUpdaterJar()

  /**
   * Performs a fast dry run to check that the build scripts a properly configured. It'll run compilation, build platform and plugin JAR files,
   * build searchable options index and scramble the main JAR, but won't produce the product archives and installation files which occupy a lot
   * of disk space and take a long time to build.
   */
  abstract void runTestBuild()

  abstract void buildUnpackedDistribution(String targetDirectory, boolean includeBinAndRuntime)

  static BuildTasks create(BuildContext context) {
    return new BuildTasksImpl(context)
  }

  /**
   * Produces distributions for all operating systems. This method must be invoked from a gant script.
   * @param productPropertiesClassName qualified name of a Groovy class which extends {@link ProductProperties} and describes the product.
   * The class must have single constructor with single {@code projectHome} parameter of type {@code String}.
   * @param groovyRootRelativePaths paths to root folders containing {@code productPropertiesClassName} and required classes, relative to project home
   * @param communityHomeRelativePath path to a directory containing sources from idea/community Git repository relative to project home
   */
  static void buildProduct(String productPropertiesClassName, List<String> groovyRootRelativePaths,
                           String communityHomeRelativePath, Script gantScript,
                           ProprietaryBuildTools proprietaryBuildTools = ProprietaryBuildTools.DUMMY) {
    try {
      BuildContext context = createBuildContextFromProduct(productPropertiesClassName, groovyRootRelativePaths,
                                                           communityHomeRelativePath, gantScript, proprietaryBuildTools)
      create(context).buildDistributions()
    } catch (Throwable ex) {
      // Print exception trace in any case. Sometimes exception handling at higher level may skip printing stacktraces.
      ex.printStackTrace()
      throw ex
    }
  }

  static void compileModulesFromProduct(String productPropertiesClassName, List<String> groovyRootRelativePaths,
                             String communityHomeRelativePath, Script gantScript,
                             ProprietaryBuildTools proprietaryBuildTools = ProprietaryBuildTools.DUMMY) {
    BuildContext context = createBuildContextFromProduct(productPropertiesClassName, groovyRootRelativePaths,
      communityHomeRelativePath, gantScript, proprietaryBuildTools)

    create(context).compileModulesFromProduct()
  }

  @CompileDynamic
  static BuildContext createBuildContextFromProduct(String productPropertiesClassName, List<String> groovyRootRelativePaths,
                                                    String communityHomeRelativePath, Script gantScript,
                                                    ProprietaryBuildTools proprietaryBuildTools = ProprietaryBuildTools.DUMMY) {
    String projectHome = IdeaProjectLoader.guessHome(gantScript)
    GantBinding binding = (GantBinding) gantScript.binding
    groovyRootRelativePaths.each {
      BuildUtils.addToClassPath("$projectHome/$it", binding.ant)
    }
    ProductProperties productProperties = (ProductProperties) Class.forName(productPropertiesClassName).constructors[0].newInstance(projectHome)

    BuildContext context = BuildContext.createContext("$projectHome/$communityHomeRelativePath", projectHome,
                                                      productProperties, proprietaryBuildTools)
    return context
  }
}