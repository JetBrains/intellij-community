package org.jetbrains.jps.idea

import org.jetbrains.jps.MacroExpander
import org.jetbrains.jps.Module

/**
 * @author nik
 */
public abstract class FacetTypeService {
  final String typeId

  FacetTypeService(String typeId) {
    this.typeId = typeId
  }

  public abstract Facet createFacet(Module module, String name, Node facetConfiguration, MacroExpander macroExpander)

}