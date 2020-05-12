// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.



package org.jetbrains.intellij.build.impl


import com.intellij.util.PathUtilRt
import com.intellij.util.SystemProperties
import com.intellij.util.containers.MultiMap
import com.intellij.util.containers.Stack
import org.apache.tools.ant.AntClassLoader
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.impl.projectStructureMapping.*
import org.jetbrains.jps.model.artifact.JpsArtifact
import org.jetbrains.jps.model.artifact.JpsArtifactService
import org.jetbrains.jps.model.artifact.elements.JpsArchivePackagingElement
import org.jetbrains.jps.model.artifact.elements.JpsLibraryFilesPackagingElement
import org.jetbrains.jps.model.java.JpsProductionModuleOutputPackagingElement
import org.jetbrains.jps.model.java.JpsTestModuleOutputPackagingElement
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference

import java.util.regex.Pattern
/**
 * Use this class to pack output of modules and libraries into JARs and lay out them by directories. It delegates the actual work to
 * {@link jetbrains.antlayout.tasks.LayoutTask}.
 */
class LayoutBuilder {
  public static final Pattern JAR_NAME_WITH_VERSION_PATTERN = ~/(.*)-\d+(?:\.\d+)*\.jar*/
  
  private final AntBuilder ant
  private final boolean compressJars
  private final MultiMap<String, String> moduleOutputPatches = MultiMap.createLinked()
  private final CompilationContext context

  LayoutBuilder(CompilationContext context, boolean compressJars) {
    ant = context.ant
    this.context = context
    this.compressJars = compressJars

    def contextLoaderRef = "GANT_CONTEXT_CLASS_LOADER"
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
    moduleOutputPatches.putValue(moduleName, pathToDirectoryWithPatchedFiles)
  }

  /**
   * Creates the output layout accordingly to {@code data} in {@code targetDirectory}. Please note that {@code data} may refer to local
   * variables of its method only. It cannot refer to its fields or methods because data's 'owner' field is changed to support the internal DSL.
   */
  void layout(String targetDirectory, @DelegatesTo(LayoutSpec) Closure data) {
    process(targetDirectory, new ProjectStructureMapping(), true, data)
  }

  void process(String targetDirectory, ProjectStructureMapping mapping, boolean copyFiles, @DelegatesTo(LayoutSpec) Closure data) {
    def spec = new LayoutSpec(mapping, copyFiles)
    //we cannot set 'spec' as delegate because 'delegate' will be overwritten by AntBuilder
    def body = data.rehydrate(null, spec, data.thisObject)
    body.resolveStrategy = Closure.OWNER_FIRST
    context.messages.debug("Creating layout in $targetDirectory:")
    ant.layout(toDir: targetDirectory, body)
    context.messages.debug("Finish creating layout in $targetDirectory.")
  }

  class LayoutSpec {
    final ProjectStructureMapping projectStructureMapping
    final Stack<String> currentPath = new Stack<>()
    private final boolean copyFiles

    LayoutSpec(ProjectStructureMapping projectStructureMapping, boolean copyFiles) {
      this.copyFiles = copyFiles
      this.projectStructureMapping = projectStructureMapping
    }

    /**
     * Create a JAR file with name {@code relativePath} (it may also include parent directories names for the JAR) into the current place
     * in the layout. The content of the JAR is specified by {@code body}.
     */
    void jar(String relativePath, boolean preserveDuplicates = false, boolean mergeManifests = true, Closure body) {
      def directory = PathUtilRt.getParentPath(relativePath)
      if (directory == "") {
        currentPath.push(relativePath)
        if (copyFiles) {
          ant.jar(name: relativePath, compress: compressJars, duplicate: preserveDuplicates ? "preserve" : "fail",
                  filesetmanifest: mergeManifests ? "merge" : "skip", body)
        }
        else {
          body()
        }
        currentPath.pop()
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
    void zip(String relativePath, Closure body) {
      def directory = PathUtilRt.getParentPath(relativePath)
      if (directory == "") {
        currentPath.push(relativePath)
        if (copyFiles) {
          ant.zip(name: relativePath, body)
        }
        else {
          body()
        }
        currentPath.pop()
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
    void dir(String relativePath, Closure body) {
      def parent = PathUtilRt.getParentPath(relativePath)
      if (relativePath.empty) {
        body()
      }
      else if (parent.empty) {
        currentPath.push(relativePath)
        if (copyFiles) {
          ant.dir(name: relativePath, body)
        }
        else {
          body()
        }
        currentPath.pop()
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
    void modulePatches(Collection<String> moduleNames, Closure body = {}) {
      moduleNames.each { String moduleName ->
        moduleOutputPatches.get(moduleName).each {
          ant.fileset(dir: it, body)
          context.messages.debug(" include $it with pathces for module '$moduleName'")
        }
      }
    }

    /**
     * Include production output of {@code moduleName} to the current place in the layout
     */
    void module(String moduleName, Closure body = {}) {
      projectStructureMapping.addEntry(new ModuleOutputEntry(getCurrentPathString(), moduleName))
      if (copyFiles) {
        ant.module(name: moduleName, body)
      }
      context.messages.debug(" include output of module '$moduleName'")
    }

    /**
     * Include test output of {@code moduleName} to the current place in the layout
     */
    void moduleTests(String moduleName, Closure body = {}) {
      projectStructureMapping.addEntry(new ModuleTestOutputEntry(getCurrentPathString(), moduleName))
      if (copyFiles) {
        ant.moduleTests(name: moduleName, body)
      }
      context.messages.debug(" include tests of module '$moduleName'")
    }

    private String getCurrentPathString() {
      currentPath.join("/")
    }

    /**
     * Include JARs added as classes roots of project library {@code libraryName} to the current place in the layout
     * @param removeVersionFromJarName if {@code true} versions will be removed from the JAR names. <strong>It may be used to temporary
     * keep names of JARs included into bootstrap classpath only.</strong>
     */
    void projectLibrary(String libraryName, boolean removeVersionFromJarName = false) {
      def library = context.project.libraryCollection.findLibrary(libraryName)
      if (library == null) {
        throw new IllegalArgumentException("Cannot find library $libraryName in the project")
      }
      jpsLibrary(library, removeVersionFromJarName)
    }

    /**
     * Include output of a project artifact {@code artifactName} to the current place in the layout
     */
    void artifact(String artifactName) {
      def artifact = JpsArtifactService.instance.getArtifacts(context.project).find {it.name == artifactName}
      if (artifact == null) {
        throw new IllegalArgumentException("Cannot find artifact $artifactName in the project")
      }

      if (copyFiles) {
        if (artifact.outputFilePath != artifact.outputPath) {
          ant.fileset(file: artifact.outputFilePath)
        }
        else {
          ant.fileset(dir: artifact.outputPath)
        }
      }
      addArtifactMapping(artifact)
      context.messages.debug(" include artifact '$artifactName'")
    }

    /**
     * Include JARs added as classes roots of a module library {@code libraryName} from module {@code moduleName} to the current place in the layout
     */
    void moduleLibrary(String moduleName, String libraryName) {
      def module = findModule(moduleName)
      def library = module.libraryCollection.libraries.find {getLibraryName(it) == libraryName}
      if (library == null) {
        throw new IllegalArgumentException("Cannot find library $libraryName in '$moduleName' module")
      }
      jpsLibrary(library)
    }

    /**
     * @param removeVersionFromJarName if {@code true} versions will be removed from the JAR names. <strong>It may be used to temporary
     *      * keep names of JARs included into bootstrap classpath only.</strong>
     **/
    void jpsLibrary(JpsLibrary library, boolean removeVersionFromJarName = false) {
      library.getFiles(JpsOrderRootType.COMPILED).each {
        def matcher = it.name =~ JAR_NAME_WITH_VERSION_PATTERN
        if (removeVersionFromJarName && matcher.matches()) {
          def newName = matcher.group(1) + ".jar"
          if (copyFiles) {
            ant.renamedFile(filePath: it.absolutePath, newName: newName)
          }
          addLibraryMapping(library, newName, it.absolutePath)
          context.messages.debug(" include $newName (renamed from $it.absolutePath) from library '${getLibraryName(library)}'")
        }
        else {
          if (copyFiles) {
            ant.fileset(file: it.absolutePath)
          }
          addLibraryMapping(library, it.name, it.absolutePath)
          context.messages.debug(" include $it.name ($it.absolutePath) from library '${getLibraryName(library)}'")
        }
      }
    }

    /**
     * Include files and directories specified in {@code inner} into the current place in this layout
     */
    void include(@DelegatesTo(LayoutSpec) Closure inner) {
      def body = inner.rehydrate(null, this, inner.thisObject)
      body.resolveStrategy = Closure.OWNER_FIRST
      body()
    }

    JpsModule findModule(String name) {
      context.findRequiredModule(name)
    }

    static String getLibraryName(JpsLibrary lib) {
      def name = lib.name
      if (name.startsWith("#")) {
        if (lib.getRoots(JpsOrderRootType.COMPILED).size() != 1) {
          def urls = lib.getRoots(JpsOrderRootType.COMPILED).collect { it.url }
          throw new IllegalStateException("Non-single entry module library $name: $urls")
        }
        File file = lib.getFiles(JpsOrderRootType.COMPILED)[0]
        return file.name
      }
      return name
    }

    private void addLibraryMapping(JpsLibrary library, String outputFileName, String libraryFilePath) {
      def outputFilePath = getCurrentPathString().isEmpty() ? outputFileName : getCurrentPathString() + "/" + outputFileName
      def parentReference = library.createReference().parentReference
      if (parentReference instanceof JpsModuleReference) {
        def projectHome = context.paths.projectHome + File.separator
        def mavenLocalRepo = new File(SystemProperties.getUserHome(), ".m2/repository").absolutePath + File.separator
        String shortenedPath = libraryFilePath.replace(projectHome, "\$PROJECT_DIR\$/").replace(mavenLocalRepo, "\$MAVEN_REPOSITORY\$/")
        projectStructureMapping.addEntry(new ModuleLibraryFileEntry(outputFilePath, shortenedPath))
      }
      else {
        projectStructureMapping.addEntry(new ProjectLibraryEntry(outputFilePath, library.name))
      }
    }

    private void addArtifactMapping(JpsArtifact artifact) {
      def rootElement = artifact.getRootElement()
      String artifactFilePath = getCurrentPathString()
      if ((rootElement instanceof JpsArchivePackagingElement)) {
        artifactFilePath += "/$rootElement.archiveName"
      }
      rootElement.children.each {
        if (it instanceof JpsProductionModuleOutputPackagingElement) {
          projectStructureMapping.addEntry(new ModuleOutputEntry(artifactFilePath, it.moduleReference.moduleName))
        }
        else if (it instanceof JpsTestModuleOutputPackagingElement) {
          projectStructureMapping.addEntry(new ModuleTestOutputEntry(artifactFilePath, it.moduleReference.moduleName))
        }
        else if (it instanceof JpsLibraryFilesPackagingElement) {
          addLibraryMapping(it.libraryReference.resolve(), artifactFilePath)
        }
      }
    }
  }
}

