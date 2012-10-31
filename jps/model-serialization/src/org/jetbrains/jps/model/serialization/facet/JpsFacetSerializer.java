package org.jetbrains.jps.model.serialization.facet;

import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleReference;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;

import java.util.List;

/**
 * @author nik
 */
public class JpsFacetSerializer {
  @NonNls public static final String FACET_TAG = "facet";
  @NonNls public static final String TYPE_ATTRIBUTE = "type";
  @NonNls public static final String CONFIGURATION_TAG = "configuration";
  @NonNls public static final String NAME_ATTRIBUTE = "name";

  public static void loadFacets(JpsModule module, @Nullable Element facetManagerElement) {
    if (facetManagerElement == null) return;
    final FacetManagerState state = XmlSerializer.deserialize(facetManagerElement, FacetManagerState.class);
    if (state != null) {
      addFacets(module, state.getFacets(), null);
    }
  }

  public static void saveFacets(JpsModule module, @NotNull Element facetManagerElement) {
    FacetManagerState managerState = new FacetManagerState();
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsFacetConfigurationSerializer<?> serializer : extension.getFacetConfigurationSerializers()) {
        if (serializer.hasExtension(module)) {
          serializer.saveExtension(module, managerState.getFacets());
        }
      }
    }
    XmlSerializer.serializeInto(managerState, facetManagerElement, new SkipDefaultValuesSerializationFilters());
  }

  private static void addFacets(JpsModule module, List<FacetState> facets, @Nullable final JpsElement parentFacet) {
    for (FacetState facetState : facets) {
      final JpsFacetConfigurationSerializer<?> serializer = getModuleExtensionSerializer(facetState.getFacetType());
      if (serializer != null) {
        final JpsElement element = addExtension(module, serializer, facetState, parentFacet);
        addFacets(module, facetState.getSubFacets(), element);
      }
    }
  }

  private static <E extends JpsElement> E addExtension(JpsModule module, JpsFacetConfigurationSerializer<E> serializer, FacetState facet,
                                                       JpsElement parentFacet) {
    return serializer.loadExtension(facet.getConfiguration(), facet.getName(), module, parentFacet);
  }

  @Nullable
  private static JpsFacetConfigurationSerializer<?> getModuleExtensionSerializer(@NotNull String typeId) {
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsFacetConfigurationSerializer<?> serializer : extension.getFacetConfigurationSerializers()) {
        if (serializer.getFacetTypeId().equals(typeId)) {
          return serializer;
        }
      }
    }
    return null;
  }

  public static JpsModuleReference createModuleReference(String facetId) {
    String moduleName = facetId.substring(0, facetId.indexOf('/'));
    return JpsElementFactory.getInstance().createModuleReference(moduleName);
  }

  public static String getFacetId(final JpsModuleReference moduleReference, final String facetTypeId, final String facetName) {
    return moduleReference.getModuleName() + "/" + facetTypeId + "/" + facetName;
  }
}
