package org.jetbrains.jps.javaee

import org.jetbrains.jps.idea.Facet

/**
 * @author nik
 */
class JavaeeFacet extends Facet {
  final List<Map<String, String>> descriptors = []
  final List<Map<String, String>> webRoots = []
}
