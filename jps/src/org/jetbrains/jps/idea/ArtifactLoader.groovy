package org.jetbrains.jps.idea

import org.jetbrains.jps.Project
import org.jetbrains.jps.artifacts.*

/**
 * @author nik
 */
class ArtifactLoader {
  private final Project project;
  private final String projectBasePath

  def ArtifactLoader(Project project, String projectBasePath) {
    this.project = project;
    this.projectBasePath = projectBasePath;
  }

  LayoutElement loadLayoutElement(Node tag, String artifactName) {
    String id = tag."@id";
    switch (id) {
      case "root":
        return new RootElement(loadChildren(tag, artifactName));
      case "directory":
        return new DirectoryElement(tag."@name", loadChildren(tag, artifactName));
      case "archive":
        return new ArchiveElement(tag."@name", loadChildren(tag, artifactName));
      case "artifact":
        return new ArtifactLayoutElement(artifactName: tag."@artifact-name")
      case "file-copy":
        return new FileCopyElement(filePath: IdeaProjectLoader.expandProjectMacro(tag."@path", projectBasePath),
                                   outputFileName: tag."@output-file-name");
      case "dir-copy":
        return new DirectoryCopyElement(dirPath: IdeaProjectLoader.expandProjectMacro(tag."@path", projectBasePath));
      case "javaee-facet-resources":
        return new JavaeeFacetResourcesElement(facetId: tag."@facet");
      case "javaee-facet-classes":
        String facetId = tag."@facet"
        return new ModuleOutputElement(moduleName: facetId.substring(0, facetId.indexOf('/')))
      case "module-output":
        return new ModuleOutputElement(moduleName: tag."@name");
      case "library":
        return new LibraryFilesElement(libraryLevel: tag."@level", libraryName: tag."@name");
    }
    project.error("unknown element in '$artifactName' artifact: $id");
  }

  List<LayoutElement> loadChildren(Node node, String artifactName) {
    node.element.collect { loadLayoutElement(it, artifactName) }
  }
}
