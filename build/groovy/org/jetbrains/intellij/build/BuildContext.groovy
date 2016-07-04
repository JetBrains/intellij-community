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

import org.codehaus.gant.GantBuilder
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.jps.gant.JpsGantProjectBuilder
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule

/**
 * @author nik
 */
abstract class BuildContext {
  GantBuilder ant
  BuildMessages messages
  BuildPaths paths
  JpsProject project
  ApplicationInfoProperties applicationInfo
  JpsGantProjectBuilder projectBuilder
  ProductProperties productProperties
  MacHostProperties macHostProperties
  BuildOptions options
  SignTool signTool

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
   * without spaces with added major version ('IntelliJIdea2016.1' for IntelliJ IDEA 2016.1)
   */
  String systemSelector

  /**
   * Base name for script files (*.bat, *.sh, *.exe), usually a shortened product name in lower case (e.g. 'idea' for IntelliJ IDEA, 'datagrip' for DataGrip)
   */
  String fileNamePrefix

  /**
   * Names of JARs inside IDE_HOME/lib directory which need to be added to bootclasspath to start the IDE
   */
  List<String> bootClassPathJarNames

  abstract void notifyArtifactBuilt(String artifactPath)

  abstract File findApplicationInfoInSources()

  abstract JpsModule findApplicationInfoModule()

  abstract JpsModule findModule(String name)

  abstract void signExeFile(String path)

  abstract void executeStep(String stepMessage, String stepId, Closure step)

  public static BuildContext createContext(GantBuilder ant, JpsGantProjectBuilder projectBuilder, JpsProject project, JpsGlobal global,
                                           String communityHome, String projectHome, String buildOutputRoot, ProductProperties productProperties,
                                           BuildOptions options = new BuildOptions(), MacHostProperties macHostProperties = null, SignTool signTool = null) {
    return new BuildContextImpl(ant, projectBuilder, project, global, communityHome, projectHome, buildOutputRoot, productProperties,
                                options, macHostProperties, signTool)
  }
}

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
   * Path to a directory where temporary files required for a particular build steps can be stored
   */
  String temp

  /**
   * Path to a directory containing JDK (currently Java 8) which is used to compile the project
   */
  String jdkHome

  /**
   * Path to a directory containing distribution of JRE for Windows which will be bundled with the product
   */
  String winJre

  String oracleWinJre
  /**
   * Path to a directory containing distribution of JRE for Linux which will be bundled with the product
   */
  String linuxJre

  /**
   * Path to a .tar.gz archive containing distribution of JRE for Mac OS X which will be bundled with the product
   */
  String macJreTarGz
}

interface BuildMessages {
  void info(String message)
  void warning(String message)

  /**
   * Report an error and stop the build process
   */
  void error(String message)

  void progress(String message)
  public <V> V block(String blockName, Closure<V> body)
}