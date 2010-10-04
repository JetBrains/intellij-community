package org.jetbrains.jps.javaee

import org.jetbrains.jps.Project
import org.jetbrains.jps.artifacts.*
import org.jetbrains.jps.idea.Facet
import org.jetbrains.jps.idea.IdeaProjectLoadingUtil

/**
 * @author nik
 */
class JavaeeFacetResourcesElement extends ComplexLayoutElement {
  String facetId

  List<LayoutElement> getSubstitution(Project project) {
    Facet facet = IdeaProjectLoadingUtil.findFacetById(project, facetId)

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
