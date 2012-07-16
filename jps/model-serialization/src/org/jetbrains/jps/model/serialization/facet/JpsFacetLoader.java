package org.jetbrains.jps.model.serialization.facet;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElementProperties;
import org.jetbrains.jps.model.module.JpsFacet;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsModelLoaderExtension;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.List;

/**
 * @author nik
 */
public class JpsFacetLoader {
  @NonNls public static final String FACET_ELEMENT = "facet";
  @NonNls public static final String TYPE_ATTRIBUTE = "type";
  @NonNls public static final String CONFIGURATION_ELEMENT = "configuration";
  @NonNls public static final String NAME_ATTRIBUTE = "name";

  public static void loadFacets(JpsModule module, @Nullable Element facetManagerElement) {
    if (facetManagerElement == null) return;
    final FacetManagerState state = XmlSerializer.deserialize(facetManagerElement, FacetManagerState.class);
    if (state != null) {
      addFacets(module, state.getFacets(), null);
    }
  }

  private static void addFacets(JpsModule module, List<FacetState> facets, @Nullable final JpsFacet parentFacet) {
    for (FacetState facetState : facets) {
      final JpsFacetPropertiesLoader<?> loader = getFacetPropertiesLoader(facetState.getFacetType());
      if (loader != null) {
        final JpsFacet facet = addFacet(module, loader, facetState);
        if (parentFacet != null) {
          facet.setParentFacet(parentFacet);
        }
        addFacets(module, facetState.getSubFacets(), facet);
      }
    }
  }

  private static <P extends JpsElementProperties> JpsFacet addFacet(JpsModule module, JpsFacetPropertiesLoader<P> loader, FacetState facet) {
    return module.addFacet(facet.getName(), loader.getType(), loader.loadProperties(facet.getConfiguration()));
  }

  @Nullable
  private static JpsFacetPropertiesLoader<?> getFacetPropertiesLoader(@NotNull String typeId) {
    for (JpsModelLoaderExtension extension : JpsServiceManager.getInstance().getExtensions(JpsModelLoaderExtension.class)) {
      for (JpsFacetPropertiesLoader<?> loader : extension.getFacetPropertiesLoaders()) {
        if (loader.getTypeId().equals(typeId)) {
          return loader;
        }
      }
    }
    return null;
  }

}
