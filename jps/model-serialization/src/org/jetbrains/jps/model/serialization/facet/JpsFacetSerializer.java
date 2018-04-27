/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    addFacets(module, state.getFacets(), null);
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
    Element facetConfiguration = facet.getConfiguration();
    return serializer.loadExtension(facetConfiguration != null ? facetConfiguration : new Element(CONFIGURATION_TAG), facet.getName(), module, parentFacet);
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
