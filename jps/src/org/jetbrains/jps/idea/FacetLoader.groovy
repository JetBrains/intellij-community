package org.jetbrains.jps.idea

import org.jetbrains.jps.Module

/**
 * @author nik
 */
class FacetLoader {
  private final Module module;
  private final String projectBasePath;
  private final String moduleBasePath;
  private static final Map<String, String> OUTPUT_PATHS = [
          "web.xml": "WEB-INF",
          "ejb-jar.xml": "META-INF",
          "application.xml": "META-INF"
  ]

  def FacetLoader(module, projectBasePath, moduleBasePath) {
    this.module = module;
    this.projectBasePath = projectBasePath;
    this.moduleBasePath = moduleBasePath;
  }


  def loadFacets(Node facetManagerTag) {
    def javaeeTypes = ["web", "ejb", "javaeeApplication"] as Set
    facetManagerTag.facet.each {Node facetTag ->
      def type = facetTag."@type"
      if (type in javaeeTypes) {
        def facet = new JavaeeFacet(name: facetTag."@name")
        facetTag.configuration?.descriptors?.deploymentDescriptor?.each {Node tag ->
          def outputPath = OUTPUT_PATHS[tag."@name"]
          if (outputPath == null) {
            outputPath = ""
          }
          facet.descriptors << [path: urlToPath(tag."@url"), outputPath: outputPath]
        }
        facetTag.configuration?.webroots?.root?.each {Node tag ->
          facet.webRoots << [path: urlToPath(tag."@url"), outputPath: tag."@relative"]
        }
        def id = "${module.name}/$type/${facet.name}"
        module.facets[id] = facet;
      }
    }
  }

  def urlToPath(String url) {
    return IdeaProjectLoader.expandMacro(IdeaProjectLoader.pathFromUrl(url), projectBasePath, moduleBasePath)
  }
}
