package org.jetbrains.jps.model.module;

import java.util.EventListener;

/**
 * @author nik
 */
public interface JpsFacetListener extends EventListener {
  void facetAdded(JpsFacet facet);
  void facetRemoved(JpsFacet facet);
}
