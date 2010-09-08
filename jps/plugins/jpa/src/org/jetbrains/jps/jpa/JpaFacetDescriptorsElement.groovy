package org.jetbrains.jps.jpa

import org.jetbrains.jps.Project
import org.jetbrains.jps.artifacts.*

/**
 * @author nik
 */
class JpaFacetDescriptorsElement extends ComplexLayoutElement {
  String facetId

  List<LayoutElement> getSubstitution(Project project) {
    def moduleName = facetId.substring(0, facetId.indexOf('/'))
    def facet = project.modules[moduleName]?.facets[facetId]
    if (facet == null) {
      project.error("Unknown facet id: $facetId")
    }

    if (!(facet instanceof JpaFacet)) {
      project.error("$facetId facet is not JPA facet")
    }

    List<LayoutElement> result = []
    facet.descriptors.each {String path ->
      result << LayoutElementFactory.createParentDirectories("META-INF", new FileCopyElement(filePath: path))
    }
    return result
  }
}
