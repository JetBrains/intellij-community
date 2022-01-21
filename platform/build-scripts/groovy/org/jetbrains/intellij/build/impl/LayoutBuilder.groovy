// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.text.Strings
import com.intellij.util.PathUtilRt
import com.intellij.util.io.URLUtil
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.apache.tools.ant.AntClassLoader
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.impl.projectStructureMapping.*
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsLibraryRoot
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference

import java.nio.file.Path
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Use this class to pack output of modules and libraries into JARs and lay out them by directories. It delegates the actual work to
 * {@link jetbrains.antlayout.tasks.LayoutTask}.
 */
@CompileStatic
final class LayoutBuilder {
  public static final Pattern JAR_NAME_WITH_VERSION_PATTERN = ~/(.*)-\d+(?:\.\d+)*\.jar*/

  private final AntBuilder ant
  private final CompilationContext context

  @CompileStatic(TypeCheckingMode.SKIP)
  LayoutBuilder(CompilationContext context) {
    ant = context.ant
    this.context = context

    def contextLoaderRef = "GANT_CONTEXT_CLASS_LOADER"
    if (!ant.project.hasReference(contextLoaderRef)) {
      ClassLoader contextLoader = getAntClassLoader(ant)
      ant.project.addReference(contextLoaderRef, contextLoader)
      ant.taskdef(name: "layout", loaderRef: contextLoaderRef, classname: "jetbrains.antlayout.tasks.LayoutTask")
    }
  }

  /** this code is extracted to a method to work around Groovy compiler bug (https://issues.apache.org/jira/projects/GROOVY/issues/GROOVY-10457) */
  private ClassLoader getAntClassLoader(AntBuilder ant) {
    ClassLoader contextLoader = getClass().getClassLoader()
    if (!(contextLoader instanceof AntClassLoader)) {
      contextLoader = new AntClassLoader(contextLoader, ant.project, null)
    }
    contextLoader
  }

  /**
   * JARs is always uncompressed
   */
  @SuppressWarnings("unused")
  @Deprecated
  LayoutBuilder(CompilationContext context, boolean compressJars) {
    this(context)
  }

  static String getLibraryName(JpsLibrary lib) {
    String name = lib.name
    if (!name.startsWith("#")) {
      return name
    }

    List<JpsLibraryRoot> roots = lib.getRoots(JpsOrderRootType.COMPILED)
    if (roots.size() != 1) {
      List<String> urls = roots.collect { it.url }
      throw new IllegalStateException("Non-single entry module library $name: $urls")
    }
    return PathUtilRt.getFileName(Strings.trimEnd(roots.get(0).url, URLUtil.JAR_SEPARATOR))
  }

  /**
   * Creates the output layout accordingly to {@code data} in {@code targetDirectory}. Please note that {@code data} may refer to local
   * variables of its method only. It cannot refer to its fields or methods because data's 'owner' field is changed to support the internal DSL.
   */
  void layout(String targetDirectory, @DelegatesTo(LayoutSpec) Closure data) {
    process(targetDirectory, new ProjectStructureMapping(), true, data)
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  void process(String targetDirectory, ProjectStructureMapping mapping, boolean copyFiles, @DelegatesTo(LayoutSpec) Closure data) {
    process(targetDirectory, createLayoutSpec(mapping, copyFiles), data)
  }

  LayoutSpec createLayoutSpec(ProjectStructureMapping mapping, boolean copyFiles) {
    return new LayoutSpec(mapping, copyFiles, context, ant)
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  void process(String targetDirectory, LayoutSpec spec, @DelegatesTo(LayoutSpec) Closure data) {
    // we cannot set 'spec' as delegate because 'delegate' will be overwritten by AntBuilder
    Closure body = data.rehydrate(null, spec, data.thisObject)
    body.resolveStrategy = Closure.OWNER_FIRST
    context.messages.debug("Creating layout in $targetDirectory:")
    ant.layout(toDir: targetDirectory, body)
    context.messages.debug("Finish creating layout in $targetDirectory.")
  }

  final static class LayoutSpec {
    final ProjectStructureMapping projectStructureMapping
    final Deque<String> currentPath = new ArrayDeque<>()
    final boolean copyFiles
    private final AntBuilder ant
    private final CompilationContext context

    private LayoutSpec(ProjectStructureMapping projectStructureMapping,
                       boolean copyFiles,
                       CompilationContext context,
                       AntBuilder ant) {
      this.copyFiles = copyFiles
      this.ant = ant
      this.projectStructureMapping = projectStructureMapping
      this.context = context
    }

    /**
     * Create a JAR file with name {@code relativePath} (it may also include parent directories names for the JAR) into the current place
     * in the layout. The content of the JAR is specified by {@code body}.
     */
    @CompileStatic(TypeCheckingMode.SKIP)
    void jar(String relativePath, boolean preserveDuplicates = false, boolean mergeManifests = true, Closure body) {
      String directory = PathUtilRt.getParentPath(relativePath)
      if (directory.isEmpty()) {
        currentPath.addLast(relativePath)
        if (copyFiles) {
          ant.jar(name: relativePath, compress: false, duplicate: preserveDuplicates ? "preserve" : "fail",
                  filesetmanifest: mergeManifests ? "merge" : "skip", body)
        }
        else {
          body()
        }
        currentPath.removeLast()
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
    @CompileStatic(TypeCheckingMode.SKIP)
    void zip(String relativePath, Closure body) {
      def directory = PathUtilRt.getParentPath(relativePath)
      if (directory == "") {
        currentPath.addLast(relativePath)
        if (copyFiles) {
          ant.zip(name: relativePath, body)
        }
        else {
          body()
        }
        currentPath.removeLast()
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
    @CompileStatic(TypeCheckingMode.SKIP)
    void dir(String relativePath, Closure body) {
      String parent = PathUtilRt.getParentPath(relativePath)
      if (relativePath.empty) {
        body()
      }
      else if (parent.empty) {
        currentPath.addLast(relativePath)
        if (copyFiles) {
          ant.dir(name: relativePath, body)
        }
        else {
          body()
        }
        currentPath.removeLast()
      }
      else {
        dir(parent) {
          dir(PathUtilRt.getFileName(relativePath), body)
        }
      }
    }

    /**
     * Include production output of {@code moduleName} to the current place in the layout
     */
    @CompileStatic(TypeCheckingMode.SKIP)
    void module(String moduleName, Closure body = {}) {
      projectStructureMapping.addEntry(new ModuleOutputEntry(Path.of(getCurrentPathString()), moduleName, 0))
      if (copyFiles) {
        ant.module(name: moduleName, body)
      }
      context.messages.debug(" include output of module '$moduleName'")
    }

    /**
     * Include test output of {@code moduleName} to the current place in the layout
     */
    @CompileStatic(TypeCheckingMode.SKIP)
    void moduleTests(String moduleName, Closure body = {}) {
      projectStructureMapping.addEntry(new ModuleTestOutputEntry(Path.of(getCurrentPathString()), moduleName))
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
      JpsLibrary library = context.project.libraryCollection.findLibrary(libraryName)
      if (library == null) {
        throw new IllegalArgumentException("Cannot find library $libraryName in the project")
      }
      jpsLibrary(library, removeVersionFromJarName)
    }

    /**
     * Include JARs added as classes roots of a module library {@code libraryName} from module {@code moduleName} to the current place in the layout
     */
    void moduleLibrary(String moduleName, String libraryName) {
      JpsModule module = findModule(moduleName)
      JpsLibrary library = module.libraryCollection.libraries.find {getLibraryName(it) == libraryName}
      if (library == null) {
        throw new IllegalArgumentException("Cannot find library $libraryName in '$moduleName' module")
      }
      jpsLibrary(library)
    }

    /**
     * @param removeVersionFromJarName if {@code true} versions will be removed from the JAR names. <strong>It may be used to temporary
     *      * keep names of JARs included into bootstrap classpath only.</strong>
     **/
    @CompileStatic(TypeCheckingMode.SKIP)
    void jpsLibrary(JpsLibrary library, boolean removeVersionFromJarName = false) {
      for (File file in library.getFiles(JpsOrderRootType.COMPILED)) {
        Matcher matcher = file.name =~ JAR_NAME_WITH_VERSION_PATTERN
        if (removeVersionFromJarName && matcher.matches()) {
          String newName = matcher.group(1) + ".jar"
          if (copyFiles) {
            ant.renamedFile(filePath: file.absolutePath, newName: newName)
          }
          addLibraryMapping(library, newName, file.toPath())
          context.messages.debug(" include $newName (renamed from $file.absolutePath) from library '${getLibraryName(library)}'")
        }
        else {
          if (copyFiles) {
            ant.fileset(file: file.absolutePath)
          }
          addLibraryMapping(library, file.name, file.toPath())
          context.messages.debug(" include $file.name ($file.absolutePath) from library '${getLibraryName(library)}'")
        }
      }
    }

    /**
     * Include files and directories specified in {@code inner} into the current place in this layout
     */
    void include(@DelegatesTo(LayoutSpec) Closure inner) {
      Closure body = inner.rehydrate(null, this, inner.thisObject)
      body.resolveStrategy = Closure.OWNER_FIRST
      body()
    }

    JpsModule findModule(String name) {
      context.findRequiredModule(name)
    }

    private void addLibraryMapping(JpsLibrary library, String outputFileName, Path libraryFile) {
      def parentReference = library.createReference().parentReference
      if (parentReference instanceof JpsModuleReference) {
        projectStructureMapping.addEntry(new ModuleLibraryFileEntry(Path.of(getOutputFilePath(outputFileName)),
                                                                    ((JpsModuleReference)parentReference).moduleName, libraryFile, 0))
      }
      else {
        ProjectLibraryData libraryData = new ProjectLibraryData(library.name, "", ProjectLibraryData.PackMode.MERGED)
        projectStructureMapping.addEntry(new ProjectLibraryEntry(Path.of(getOutputFilePath(outputFileName)), libraryData, libraryFile, 0))
      }
    }

    String getOutputFilePath(String outputFileName) {
      return currentPath.isEmpty() ? outputFileName : getCurrentPathString() + "/" + outputFileName
    }
  }
}

