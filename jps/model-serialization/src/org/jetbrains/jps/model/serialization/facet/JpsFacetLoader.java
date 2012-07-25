package org.jetbrains.jps.model.serialization.facet;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
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

  public static void loadFacets(JpsModule module, @Nullable Element facetManagerElement, final String baseModulePath) {
    if (facetManagerElement == null) return;
    final FacetManagerState state = XmlSerializer.deserialize(facetManagerElement, FacetManagerState.class);
    if (state != null) {
      addFacets(module, state.getFacets(), null, baseModulePath);
    }
  }

  private static void addFacets(JpsModule module, List<FacetState> facets, @Nullable final JpsElement parentFacet,
                                final String baseModulePath) {
    for (FacetState facetState : facets) {
      final JpsModuleExtensionLoader<?> loader = getModuleExtensionLoader(facetState.getFacetType());
      if (loader != null) {
        final JpsElement element = addExtension(module, loader, facetState, parentFacet, baseModulePath);
        addFacets(module, facetState.getSubFacets(), element, baseModulePath);
      }
    }
  }

  private static <E extends JpsElement> E addExtension(JpsModule module, JpsModuleExtensionLoader<E> loader, FacetState facet,
                                                       JpsElement parentFacet, final String baseModulePath) {
    final E e = loader.loadElement(facet.getConfiguration(), facet.getName(), baseModulePath, parentFacet);
    return loader.addElement(module, e);
  }

  @Nullable
  private static JpsModuleExtensionLoader<?> getModuleExtensionLoader(@NotNull String typeId) {
    for (JpsModelLoaderExtension extension : JpsServiceManager.getInstance().getExtensions(JpsModelLoaderExtension.class)) {
      for (JpsModuleExtensionLoader<?> loader : extension.getModuleExtensionLoaders()) {
        if (loader.getFacetTypeId().equals(typeId)) {
          return loader;
        }
      }
    }
    return null;
  }
}
