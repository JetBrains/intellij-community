// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.conversion;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.facet.JpsFacetSerializer;

import java.util.Collection;

public final class DetachFacetConversionProcessor extends ConversionProcessor<ModuleSettings> {
  private final String[] myFacetNames;

  public DetachFacetConversionProcessor(String @NotNull ... names) {
    myFacetNames = names;
  }

  @Override
  public boolean isConversionNeeded(ModuleSettings moduleSettings) {
    for (String facetName : myFacetNames) {
      if (facetName != null && !moduleSettings.getFacetElements(facetName).isEmpty()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void process(ModuleSettings moduleSettings) throws CannotConvertException {
    final Element facetManagerElement = moduleSettings.getComponentElement(JpsFacetSerializer.FACET_MANAGER_COMPONENT_NAME);
    if (facetManagerElement == null) return;
    for (String facetName : myFacetNames) {
      for (Element element : getElements(moduleSettings, facetName)) {
        element.detach();
      }
    }
  }

  private static Element[] getElements(ModuleSettings moduleSettings, String facetName) {
    Collection<? extends Element> elements = moduleSettings.getFacetElements(facetName);
    return elements.toArray(new Element[0]);
  }
}
