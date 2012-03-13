package org.jetbrains.jps.idea

import org.jetbrains.jps.Project

/**
 * @author nik
 */
public class IdeaProjectLoadingUtil {
  static String pathFromUrl(String url) {
    if (url == null) return null
    if (url.startsWith("file://")) {
      return url.substring("file://".length())
    }
    else if (url.startsWith("jar://")) {
      url = url.substring("jar://".length())
      if (url.endsWith("!/"))
        url = url.substring(0, url.length() - "!/".length())
    }
    url
  }

  static Facet findFacetByIdWithAssertion(Project project, String facetId, ProjectLoadingErrorReporter errorReporter) {
    Facet facet = findFacetById(project, facetId)
    if (facet == null) {
      errorReporter.error("Facet not found: id=$facetId")
    }
    return facet
  }

  static findFacetById(Project project, String facetId) {
    def moduleName = facetId.substring(0, facetId.indexOf('/'))
    def module = project.modules[moduleName]
    if (module == null) return null
    return module.facets[facetId]
  }
}
