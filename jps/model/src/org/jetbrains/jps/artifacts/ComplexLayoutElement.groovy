package org.jetbrains.jps.artifacts

import org.jetbrains.jps.Project
import org.jetbrains.jps.ProjectPaths
import org.jetbrains.jps.idea.ProjectLoadingErrorReporter
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsLibraryCollection
/**
 * @author nik
 */
abstract class ComplexLayoutElement extends LayoutElement {
  abstract List<LayoutElement> getSubstitution(Project project, JpsModel model)

  boolean process(Project project, JpsModel model, Closure processor) {
    if (processor(this)) {
      getSubstitution(project, model)*.process(project, model, processor)
    }
  }
}

class LibraryFilesElement extends ComplexLayoutElement {
  public static final String PROJECT_LEVEL = "project"
  String moduleName
  String libraryName
  String libraryLevel

  List<LayoutElement> getSubstitution(Project project, JpsModel model) {
    JpsLibraryCollection libraries = null
    switch (libraryLevel) {
      case PROJECT_LEVEL:
        libraries = model.project.libraryCollection
        break
      case "module":
        libraries = model.project.modules.find {it.name.equals(moduleName)}?.libraryCollection
        break
      case "application":
        libraries = model.global.libraryCollection
        break
    }
    JpsLibrary library = libraries?.findLibrary(libraryName)
    if (library == null) {
      return []
    }

    List<File> files = new ArrayList<File>()
    ProjectPaths.addLibraryFiles(files, library)
    return files.collect {File file ->
      if (file.isDirectory()) {
        return new DirectoryCopyElement(dirPath: file.absolutePath)
      }
      else {
        return new FileCopyElement(filePath: file.absolutePath)
      }
    }
  }
}

class ArtifactLayoutElement extends ComplexLayoutElement {
  String artifactName
  ProjectLoadingErrorReporter errorReporter

  List<LayoutElement> getSubstitution(Project project, JpsModel model) {
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
