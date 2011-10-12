package org.jetbrains.jps.jpa

import org.jetbrains.jps.Project
import org.jetbrains.jps.artifacts.ComplexLayoutElement
import org.jetbrains.jps.artifacts.FileCopyElement
import org.jetbrains.jps.artifacts.LayoutElement
import org.jetbrains.jps.artifacts.LayoutElementFactory
import org.jetbrains.jps.idea.ProjectLoadingErrorReporter

/**
 * @author nik
 */
class JpaFacetDescriptorsElement extends ComplexLayoutElement {
  String facetId
  ProjectLoadingErrorReporter errorReporter

  List<LayoutElement> getSubstitution(Project project) {
    def moduleName = facetId.substring(0, facetId.indexOf('/'))
    def facet = project.modules[moduleName]?.facets[facetId]
    if (facet == null) {
      errorReporter.error("Unknown facet id: $facetId")
    }

    if (!(facet instanceof JpaFacet)) {
      errorReporter.error("$facetId facet is not JPA facet")
    }

    List<LayoutElement> result = []
    facet.descriptors.each {String path ->
      result << LayoutElementFactory.createParentDirectories("META-INF", new FileCopyElement(filePath: path))
    }
    return result
  }
}
