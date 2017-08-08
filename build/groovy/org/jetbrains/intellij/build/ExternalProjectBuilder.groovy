/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build

import com.intellij.util.containers.hash.HashMap
import org.apache.tools.ant.Project
import org.jetbrains.jps.gant.JpsGantProjectBuilder
import org.jetbrains.jps.model.module.JpsModule

import java.nio.file.Paths

class ExternalProjectBuilder extends JpsGantProjectBuilder {

  private final myProject

  // The main output of a given module.
  Map<String, String> myOutputs

  // The runtime dependencies of that module. This is needed to be able to run
  // utilities within modules while assembling. For example: action indexing.
  Map<String, List<String>> myRuntimeDeps

  ExternalProjectBuilder(Project project) {
    super(project, null)
    myProject = project
    myOutputs = new HashMap<>()
    myRuntimeDeps = new HashMap<>()

    // Parse the external information
    def data = System.getProperty("custom.project.data")
    def root = System.getProperty("custom.project.root")
    Properties properties = new Properties()
    properties.load(new FileInputStream(data))
    for (String name : properties.stringPropertyNames()) {
      def values = properties.getProperty(name).split(":")
      values = values.collect { it -> Paths.get(root, it).toString() }
      myOutputs.put(name, values[0])
      myRuntimeDeps.put(name, values[1..values.size()-1])
    }
  }

  @Override
  void setTargetFolder(String targetFolder) {
    // Do nothing
  }

  @Override
  void exportModuleOutputProperties() {
    myOutputs.each { name, jar -> myProject.setProperty("module." + name + ".output.main", jar) }
  }

  @Override
  String getModuleOutput(JpsModule module, boolean forTests) {
    return myOutputs.get(module.name)
  }

  @Override
  List<String> moduleRuntimeClasspath(JpsModule module, boolean forTests) {
    return myRuntimeDeps.get(module.name)
  }

  @Override
  void cleanOutput() {
    // Nothing to clean
  }

  @Override
  void buildModules(List<JpsModule> modules) {
    // Nothing to build
  }

  @Override
  void makeModuleTests(JpsModule module) {
    // Nothing to make
  }
}
