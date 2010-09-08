package org.jetbrains.jps.idea

import org.jetbrains.jps.MacroExpander
import org.jetbrains.jps.Module

 /**
 * @author nik
 */
class FacetLoader {
  private final Module module
  private final MacroExpander macroExpander
  private static final ServiceLoader<FacetTypeService> facetTypeLoader = ServiceLoader.load(FacetTypeService.class)
  private static Map<String, FacetTypeService> facetTypes = null

  def FacetLoader(Module module, MacroExpander macroExpander) {
    this.module = module
    this.macroExpander = macroExpander
  }


  def loadFacets(Node facetManagerTag) {
    facetManagerTag.facet.each {Node facetTag ->
      def typeId = facetTag."@type"
      FacetTypeService type = findFacetType(typeId)
      if (type != null) {
        def facet = type.createFacet(module, facetTag."@name", facetTag.configuration[0], macroExpander)
        def id = "${module.name}/$typeId/${facet.name}"
        module.facets[id] = facet;
      }
    }
  }

  private static FacetTypeService findFacetType(String typeId) {
    if (facetTypes == null) {
      facetTypes = [:]
      facetTypeLoader.each {FacetTypeService type ->
        facetTypes[type.typeId] = type
      }
    }
    return facetTypes[typeId]
  }
}
