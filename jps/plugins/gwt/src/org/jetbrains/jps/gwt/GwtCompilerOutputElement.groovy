package org.jetbrains.jps.gwt

import org.jetbrains.jps.artifacts.ComplexLayoutElement
import org.jetbrains.jps.artifacts.LayoutElement
import org.jetbrains.jps.Project
import org.jetbrains.jps.idea.IdeaProjectLoadingUtil
import org.jetbrains.jps.idea.Facet
import org.jetbrains.jps.artifacts.DirectoryCopyElement

/**
 * @author nik
 */
class GwtCompilerOutputElement extends ComplexLayoutElement {
  String facetId

  @Override
  List<LayoutElement> getSubstitution(Project project) {
    Facet facet = IdeaProjectLoadingUtil.findFacetByIdWithAssertion(project, facetId)
    if (!(facet instanceof GwtFacet)) {
      project.error("'$facetId' is not GWT facet!")
    }

    GwtFacet gwtFacet = (GwtFacet)facet
    return [new DirectoryCopyElement(dirPath: gwtFacet.tempOutputDir)]
  }

  Facet findFacet(Project project) {
    return IdeaProjectLoadingUtil.findFacetById(project, facetId)
  }
}
