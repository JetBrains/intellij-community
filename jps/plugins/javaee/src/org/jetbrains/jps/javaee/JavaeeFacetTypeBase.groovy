package org.jetbrains.jps.javaee

import org.jetbrains.jps.MacroExpander
import org.jetbrains.jps.Module
import org.jetbrains.jps.idea.Facet
import org.jetbrains.jps.idea.FacetTypeService
import org.jetbrains.jps.idea.IdeaProjectLoader

/**
 * @author nik
 */
public abstract class JavaeeFacetTypeBase extends FacetTypeService {
  protected JavaeeFacetTypeBase(String typeId) {
    super(typeId)
  }

  protected String getDescriptorOutputPath(String descriptorId) {
    return "META-INF"
  }

  @Override
  public Facet createFacet(Module module, String name, Node facetConfiguration, MacroExpander macroExpander) {
    def facet = new JavaeeFacet(name: name)
    facetConfiguration?.descriptors?.deploymentDescriptor?.each {Node tag ->
      def outputPath = getDescriptorOutputPath(tag."@name")
      String path = urlToPath(tag."@url", macroExpander)
      facet.descriptors << [path: path, outputPath: outputPath]
    }
    facetConfiguration?.webroots?.root?.each {Node tag ->
      String path = urlToPath(tag."@url", macroExpander)
      facet.webRoots << [path: path, outputPath: tag."@relative"]
    }
    return facet
  }

  def urlToPath(String url, MacroExpander macroExpander) {
    return macroExpander.expandMacros(IdeaProjectLoader.pathFromUrl(url))
  }
}
