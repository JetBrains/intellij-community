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
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.MultiValuesMap
import com.intellij.util.PathUtilRt
import org.apache.tools.ant.AntClassLoader
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule

/**
 * This is replacement for {@link org.jetbrains.jps.gant.JpsGantTool} which doesn't depend on Gant. It also allows us to patch a module
 * output before packing it into JAR.
 *
 * @author nik
 */
class LayoutBuilder {
  private final AntBuilder ant
  private final boolean compressJars
  final Set<String> usedModules = new LinkedHashSet<>()
  private final MultiValuesMap<String, String> moduleOutputPatches = new MultiValuesMap<>(true)
  private final JpsProject project

  LayoutBuilder(AntBuilder ant, JpsProject project, boolean compressJars) {
    this.project = project
    this.ant = ant
    this.compressJars = compressJars

    def contextLoaderRef = "GANT_CONTEXT_CLASS_LOADER";
    if (!ant.project.hasReference(contextLoaderRef)) {
      ClassLoader contextLoader = Thread.currentThread().contextClassLoader
      if (!(contextLoader instanceof AntClassLoader)) {
        contextLoader = new AntClassLoader(contextLoader, ant.project, null)
      }
      ant.project.addReference(contextLoaderRef, contextLoader)
      ant.taskdef(name: "layout", loaderRef: contextLoaderRef, classname: "jetbrains.antlayout.tasks.LayoutTask")
    }
  }

  /**
   * Contents of {@code pathToDirectoryWithPatchedFiles} will be used to patch the module output. Set 'preserveDuplicates' to {@code true}
   * when calling {@link LayoutSpec#jar} and call {@link LayoutSpec#modulePatches} from its body to apply the patches to the JAR.
   */
  void patchModuleOutput(String moduleName, String pathToDirectoryWithPatchedFiles) {
    moduleOutputPatches.put(moduleName, pathToDirectoryWithPatchedFiles)
  }

  /**
   * Creates the output layout accordingly to {@code data} in {@code targetDirectory}. Please note that {@code data} may refer to local
   * variables of its method only. It cannot refer to its fields or methods because data's 'owner' field is changed to support the internal DSL.
   */
  void layout(String targetDirectory, @DelegatesTo(LayoutSpec) Closure data) {
    def spec = new LayoutSpec()
    //we cannot set 'spec' as delegate because 'delegate' will be overwritten by AntBuilder
    def body = data.rehydrate(null, spec, data.thisObject)
    body.resolveStrategy = Closure.OWNER_FIRST
    ant.layout(toDir: targetDirectory, body)
  }

  class LayoutSpec {
    /**
     * Create a JAR file with name {@code relativePath} (it may also include parent directories names for the JAR) into the current place
     * in the layout. The content of the JAR is specified by {@code body}.
     */
    def jar(String relativePath, boolean preserveDuplicates = false, boolean mergeManifests = true, Closure body) {
      def directory = PathUtilRt.getParentPath(relativePath)
      if (directory == "") {
        ant.jar(name: relativePath, compress: compressJars, duplicate: preserveDuplicates ? "preserve" : "fail",
                filesetmanifest: mergeManifests ? "merge" : "skip", body)
      }
      else {
        dir(directory) {
          jar(PathUtilRt.getFileName(relativePath), preserveDuplicates, mergeManifests, body)
        }
      }
    }

    /**
     * Create a Zip file with name {@code relativePath} (it may also include parent directories names for the JAR) into the current place
     * in the layout. The content of the JAR is specified by {@code body}.
     */
    def zip(String relativePath, Closure body) {
      def directory = PathUtilRt.getParentPath(relativePath)
      if (directory == "") {
        ant.zip(name: relativePath, body)
      }
      else {
        dir(directory) {
          zip(PathUtilRt.getFileName(relativePath), body)
        }
      }
    }

    /**
     * Create a directory (or several nested directories) {@code relativePath} in the current place in the layout. The content of the
     * directory is specified by {@code body}.
     */
    def dir(String relativePath, Closure body) {
      def parent = PathUtilRt.getParentPath(relativePath)
      if (relativePath.empty) {
        body()
      }
      else if (parent.empty) {
        ant.dir(name: relativePath, body)
      }
      else {
        dir(parent) {
          dir(PathUtilRt.getFileName(relativePath), body)
        }
      }
    }

    /**
     * Include the patched outputs of {@code moduleNames} modules to the current place in the layout. This method is supposed to be called
     * in the {@code body} of {@link #jar} with 'preserveDuplicates' set to {@code true}
     */
    def modulePatches(Collection<String> moduleNames, Closure body = {}) {
      moduleNames.each {
        moduleOutputPatches.get(it)?.each {
          ant.fileset(dir: it, body)
        }
      }
    }

    /**
     * Include production output of {@code moduleName} to the current place in the layout
     */
    def module(String moduleName, Closure body = {}) {
      usedModules << moduleName
      ant.module(name: moduleName, body)
    }

    /**
     * Include test output of {@code moduleName} to the current place in the layout
     */
    def moduleTests(String moduleName, Closure body = {}) {
      ant.moduleTests(name: moduleName, body)
    }

    /**
     * Include JARs added as classes roots of project library {@code libraryName} to the current place in the layout
     */
    def projectLibrary(String libraryName) {
      def library = project.libraryCollection.findLibrary(libraryName)
      if (library == null) {
        throw new IllegalArgumentException("Cannot find library $libraryName in the project")
      }
      jpsLibrary(library)
    }

    /**
     * Include JARs added as classes roots of a module library {@code libraryName} from module {@code moduleName} to the current place in the layout
     */
    def moduleLibrary(String moduleName, String libraryName) {
      def module = findModule(moduleName)
      def library = module.libraryCollection.libraries.find {getLibraryName(it) == libraryName}
      if (library == null) {
        throw new IllegalArgumentException("Cannot find library $libraryName in '$moduleName' module")
      }
      jpsLibrary(library)
    }

    def jpsLibrary(JpsLibrary library) {
      library.getFiles(JpsOrderRootType.COMPILED).each {
        ant.fileset(file: it.absolutePath)
      }
    }

    def JpsModule findModule(String name) {
      def module = project.modules.find { it.name == name }
      if (module == null) {
        throw new IllegalArgumentException("Cannot find module '$name' in the project")
      }
      module
    }

    private String getLibraryName(JpsLibrary lib) {
      def name = lib.name
      if (name.startsWith("#")) {
        if (lib.getRoots(JpsOrderRootType.COMPILED).size() != 1) {
          def urls = lib.getRoots(JpsOrderRootType.COMPILED).collect { it.url }
          throw new IllegalStateException("Non-single entry module library $name: $urls");
        }
        File file = lib.getFiles(JpsOrderRootType.COMPILED)[0]
        return file.name
      }
      return name
    }
  }
}

