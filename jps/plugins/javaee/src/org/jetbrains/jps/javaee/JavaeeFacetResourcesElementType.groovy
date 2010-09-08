package org.jetbrains.jps.javaee

import org.jetbrains.jps.MacroExpander
import org.jetbrains.jps.Project
import org.jetbrains.jps.artifacts.LayoutElement
import org.jetbrains.jps.artifacts.LayoutElementTypeService

 /**
 * @author nik
 */
class JavaeeFacetResourcesElementType extends LayoutElementTypeService {
  JavaeeFacetResourcesElementType() {
    super("javaee-facet-resources")
  }

  @Override
  LayoutElement createElement(Project project, Node tag, MacroExpander macroExpander) {
    return new JavaeeFacetResourcesElement(facetId: tag."@facet")
  }

}
