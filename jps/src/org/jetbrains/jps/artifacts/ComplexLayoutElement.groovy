package org.jetbrains.jps.artifacts

import org.jetbrains.jps.Library
import org.jetbrains.jps.Project
import org.jetbrains.jps.idea.JavaeeFacet

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

class JavaeeFacetResourcesElement extends ComplexLayoutElement {
  String facetId

  List<LayoutElement> getSubstitution(Project project) {
    def moduleName = facetId.substring(0, facetId.indexOf('/'))
    def facet = project.modules[moduleName]?.facets[facetId]
    if (facet == null) {
      project.error("Unknown facet id: $facetId")
    }

    if (!(facet instanceof JavaeeFacet)) {
      project.error("$facetId facet is not JavaEE facet")
    }

    List<LayoutElement> result = []
    facet.descriptors.each {Map<String, String> descriptor ->
      result << LayoutElementFactory.createParentDirectories(descriptor.outputPath, new FileCopyElement(filePath: descriptor.path))
    }
    facet.webRoots.each {Map<String, String> webRoot ->
      result << LayoutElementFactory.createParentDirectories(webRoot.outputPath, new DirectoryCopyElement(dirPath: webRoot.path))
    }
    return result
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
