package org.jetbrains.jps.gwt

import org.jetbrains.jps.artifacts.ComplexLayoutElement
import org.jetbrains.jps.artifacts.LayoutElement
import org.jetbrains.jps.Project
import org.jetbrains.jps.idea.IdeaProjectLoadingUtil
import org.jetbrains.jps.idea.Facet
import org.jetbrains.jps.artifacts.DirectoryCopyElement
import org.jetbrains.jps.idea.ProjectLoadingErrorReporter

/**
 * @author nik
 */
class GwtCompilerOutputElement extends ComplexLayoutElement {
  String facetId
  ProjectLoadingErrorReporter errorReporter

  @Override
  List<LayoutElement> getSubstitution(Project project) {
    Facet facet = IdeaProjectLoadingUtil.findFacetByIdWithAssertion(project, facetId, errorReporter)
    if (!(facet instanceof GwtFacet)) {
      errorReporter.error("'$facetId' is not GWT facet!")
    }

    GwtFacet gwtFacet = (GwtFacet)facet
    return [new DirectoryCopyElement(dirPath: gwtFacet.tempOutputDir)]
  }

  Facet findFacet(Project project) {
    return IdeaProjectLoadingUtil.findFacetById(project, facetId)
  }
}
