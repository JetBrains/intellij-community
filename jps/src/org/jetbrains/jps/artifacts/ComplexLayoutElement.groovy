package org.jetbrains.jps.artifacts

import org.jetbrains.jps.Library
import org.jetbrains.jps.Project

 /**
 * @author nik
 */
abstract class ComplexLayoutElement extends LayoutElement {
  abstract List<LayoutElement> getSubstitution(Project project)

  def build(Project project) {
    getSubstitution(project)*.build(project)
  }

  boolean process(Project project, Closure processor) {
    if (processor(this)) {
      getSubstitution(project)*.process(project, processor)
    }
  }
}

class LibraryFilesElement extends ComplexLayoutElement {
  String moduleName
  String libraryName
  String libraryLevel

  List<LayoutElement> getSubstitution(Project project) {
    Library library
    switch (libraryLevel) {
      case "project":
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

  List<LayoutElement> getSubstitution(Project project) {
    Artifact artifact = project.artifacts[artifactName]
    if (artifact == null) {
      project.error("unknown artifact: $artifactName")
    }
    def root = artifact.rootElement
    if (root instanceof RootElement) {
      return ((RootElement)root).children
    }
    return [root]
  }

  def build(Project project) {
    def artifact = findArtifact(project)
    if (artifact == null) {
      project.error("unknown artifact: $artifactName")
    }
    def output = project.artifactBuilder.artifactOutputs[artifact]
    if (output != null) {
      project.binding.ant.fileset(dir: output)
    }
    else {
      project.error("Required artifact $artifactName is not build")
//      super.build(project)
    }
  }

  Artifact findArtifact(Project project) {
    return project.artifacts[artifactName]
  }
}
