package org.jetbrains.jps.artifacts

import org.jetbrains.jps.Library
import org.jetbrains.jps.Project
import org.jetbrains.jps.idea.ProjectLoadingErrorReporter

/**
 * @author nik
 */
abstract class ComplexLayoutElement extends LayoutElement {
  abstract List<LayoutElement> getSubstitution(Project project)

  boolean process(Project project, Closure processor) {
    if (processor(this)) {
      getSubstitution(project)*.process(project, processor)
    }
  }
}

class LibraryFilesElement extends ComplexLayoutElement {
  public static final String PROJECT_LEVEL = "project"
  String moduleName
  String libraryName
  String libraryLevel

  List<LayoutElement> getSubstitution(Project project) {
    Library library
    switch (libraryLevel) {
      case PROJECT_LEVEL:
        library = project.libraries[libraryName]
        break
      case "module":
        library = project.modules[moduleName]?.libraries[libraryName]
        break
      case "application":
        library = project.globalLibraries[libraryName]
        break
    }
    if (library == null) {
      return []
    }

    return library.classpath.collect {String path ->
      if (new File(path).isDirectory()) {
        return new DirectoryCopyElement(dirPath: path)
      }
      else {
        return new FileCopyElement(filePath: path)
      }
    }
  }
}

class ArtifactLayoutElement extends ComplexLayoutElement {
  String artifactName
  ProjectLoadingErrorReporter errorReporter

  List<LayoutElement> getSubstitution(Project project) {
    Artifact artifact = project.artifacts[artifactName]
    if (artifact == null) {
      errorReporter.error("unknown artifact: $artifactName")
      return []
    }
    def root = artifact.rootElement
    if (root instanceof RootElement) {
      return ((RootElement)root).children
    }
    return [root]
  }

  Artifact findArtifact(Project project) {
    return project.artifacts[artifactName]
  }
}
