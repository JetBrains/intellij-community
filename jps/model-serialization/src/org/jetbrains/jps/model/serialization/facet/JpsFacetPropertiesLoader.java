package org.jetbrains.jps.model.serialization.facet;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementProperties;
import org.jetbrains.jps.model.module.JpsFacetType;
import org.jetbrains.jps.model.serialization.JpsElementPropertiesLoader;

/**
 * @author nik
 */
public abstract class JpsFacetPropertiesLoader<P extends JpsElementProperties> extends JpsElementPropertiesLoader<P, JpsFacetType<P>> {
  public JpsFacetPropertiesLoader(JpsFacetType<P> type, String typeId) {
    super(type, typeId);
  }

  public abstract P loadProperties(@NotNull Element facetConfigurationElement);
}
