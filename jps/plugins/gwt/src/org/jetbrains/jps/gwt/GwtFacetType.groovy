package org.jetbrains.jps.gwt

import org.jetbrains.jps.MacroExpander
import org.jetbrains.jps.Module
import org.jetbrains.jps.idea.Facet
import org.jetbrains.jps.idea.FacetTypeService

import org.jetbrains.jps.idea.IdeaProjectLoadingUtil

/**
 * @author nik
 */
class GwtFacetType extends FacetTypeService {
  GwtFacetType() {
    super("gwt")
  }

  @Override
  Facet createFacet(Module module, String name, Node facetConfiguration, MacroExpander macroExpander) {
    def facet = new GwtFacet(module: module, name: name)
    facetConfiguration.setting.each {Node child ->
      String value = child."@value"
      switch (child."@name") {
        case "compilerMaxHeapSize":
          facet.compilerMaxHeapSize = Integer.parseInt(value)
          break
        case "gwtScriptOutputStyle":
          facet.scriptOutputStyle = value
          break
        case "additionalCompilerParameters":
          facet.additionalCompilerParameters = value
          break
        case "gwtSdkUrl":
          facet.sdkPath = macroExpander.expandMacros(IdeaProjectLoadingUtil.pathFromUrl(value))
          break
      }
    }

    return facet
  }

}
