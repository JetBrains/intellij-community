package org.jetbrains.jps.javaee

import org.jetbrains.jps.Project
import org.jetbrains.jps.artifacts.*

/**
 * @author nik
 */
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
