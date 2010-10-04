package org.jetbrains.jps.jpa

import org.jetbrains.jps.idea.FacetTypeService
import org.jetbrains.jps.idea.Facet
import org.jetbrains.jps.Module
import org.jetbrains.jps.MacroExpander

import org.jetbrains.jps.idea.IdeaProjectLoadingUtil

/**
 * @author nik
 */
class JpaFacetType extends FacetTypeService {
  JpaFacetType() {
    super("jpa")
  }

  @Override
  Facet createFacet(Module module, String name, Node facetConfiguration, MacroExpander macroExpander) {
    JpaFacet facet = new JpaFacet(name: name)
    facetConfiguration?.deploymentDescriptor?.each {Node tag ->
      String path = macroExpander.expandMacros(IdeaProjectLoadingUtil.pathFromUrl(tag."@url"))
      facet.descriptors << path
    }
    return facet
  }


}
