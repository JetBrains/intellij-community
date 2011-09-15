package org.jetbrains.jps.scala

import org.jetbrains.jps.idea.FacetTypeService
import org.jetbrains.jps.idea.Facet
import org.jetbrains.jps.Module
import org.jetbrains.jps.MacroExpander

class ScalaFacetType extends FacetTypeService {
  ScalaFacetType() {
    super("scala")
  }

  @Override
  Facet createFacet(Module module, String name, Node facetConfiguration, MacroExpander macroExpander) {
    Facet facet = new ScalaFacet(name: name)
    facetConfiguration.option.each {Node child ->
      String value = child."@value"
      switch (child."@name") {
        case "compilerLibraryName":
          facet.compilerLibraryName = value
          break
      }
    }

    return facet;
  }
}
