package org.jetbrains.jps.javaee

import org.jetbrains.jps.Project
import org.jetbrains.jps.idea.Facet
import org.jetbrains.jps.idea.IdeaProjectLoadingUtil
import org.jetbrains.jps.idea.ProjectLoadingErrorReporter
import org.jetbrains.jps.artifacts.*

/**
 * @author nik
 */
class JavaeeFacetResourcesElement extends ComplexLayoutElement {
  String facetId
  ProjectLoadingErrorReporter errorReporter

  List<LayoutElement> getSubstitution(Project project) {
    Facet facet = IdeaProjectLoadingUtil.findFacetByIdWithAssertion(project, facetId, errorReporter)

    if (!(facet instanceof JavaeeFacet)) {
      errorReporter.error("$facetId facet is not JavaEE facet")
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
