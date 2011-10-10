package org.jetbrains.jps.artifacts

import org.jetbrains.jps.Library
import org.jetbrains.jps.Project
import org.jetbrains.jps.ProjectBuilder
import org.jetbrains.jps.idea.ProjectLoadingErrorReporter

/**
 * @author nik
 */
abstract class ComplexLayoutElement extends LayoutElement {
  abstract List<LayoutElement> getSubstitution(Project project)

  def build(ProjectBuilder projectBuilder) {
    getSubstitution(projectBuilder.project)*.build(projectBuilder)
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
  ProjectLoadingErrorReporter errorReporter

  List<LayoutElement> getSubstitution(Project project) {
    Artifact artifact = project.artifacts[artifactName]
    if (artifact == null) {
      errorReporter.error("unknown artifact: $artifactName")
    }
    def root = artifact.rootElement
    if (root instanceof RootElement) {
      return ((RootElement)root).children
    }
    return [root]
  }

  def build(ProjectBuilder projectBuilder) {
    def artifact = findArtifact(projectBuilder.project)
    if (artifact == null) {
      projectBuilder.error("unknown artifact: $artifactName")
    }
    def output = projectBuilder.artifactBuilder.artifactOutputs[artifact]
    if (output != null) {
      LayoutElement root = artifact.rootElement
      if (root instanceof ArchiveElement) {
        projectBuilder.binding.ant.fileset(file: "$output/$root.name")
      }
      else {
        projectBuilder.binding.ant.fileset(dir: output)
      }
    }
    else {
      projectBuilder.error("Required artifact $artifactName is not build")
    }
  }

  Artifact findArtifact(Project project) {
    return project.artifacts[artifactName]
  }
}
