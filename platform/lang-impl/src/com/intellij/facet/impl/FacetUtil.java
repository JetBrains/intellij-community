/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.facet.impl;

import com.intellij.facet.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.ComponentSerializationUtil;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.facet.JpsFacetSerializer;

import java.util.Arrays;

/**
 * @author nik
 */
public class FacetUtil {

  public static <F extends Facet> F addFacet(Module module, FacetType<F, ?> type) {
    final ModifiableFacetModel model = FacetManager.getInstance(module).createModifiableModel();
    final F facet = createFacet(module, type);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        model.addFacet(facet);
        model.commit();
      }
    });
    return facet;
  }

  private static <F extends Facet, C extends FacetConfiguration> F createFacet(final Module module, final FacetType<F, C> type) {
    return FacetManager.getInstance(module).createFacet(type, type.getPresentableName(), type.createDefaultConfiguration(), null);
  }

  public static void deleteFacet(final Facet facet) {
    new WriteAction() {
      @Override
      protected void run(@NotNull final Result result) {
        if (!isRegistered(facet)) {
          return;
        }

        ModifiableFacetModel model = FacetManager.getInstance(facet.getModule()).createModifiableModel();
        model.removeFacet(facet);
        model.commit();
      }
    }.execute();
  }

  public static boolean isRegistered(Facet facet) {
    return Arrays.asList(FacetManager.getInstance(facet.getModule()).getAllFacets()).contains(facet);
  }

  public static void loadFacetConfiguration(final @NotNull FacetConfiguration configuration, final @Nullable Element config)
      throws InvalidDataException {
    if (config != null) {
      if (configuration instanceof PersistentStateComponent) {
        ComponentSerializationUtil.loadComponentState((PersistentStateComponent)configuration, config);
      }
      else {
        configuration.readExternal(config);
      }
    }
  }

  public static Element saveFacetConfiguration(final FacetConfiguration configuration) throws WriteExternalException {
    if (configuration instanceof PersistentStateComponent) {
      Object state = ((PersistentStateComponent)configuration).getState();
      if (state instanceof Element) return ((Element)state);
      return XmlSerializer.serialize(state, new SkipDefaultValuesSerializationFilters());
    }
    else {
      final Element config = new Element(JpsFacetSerializer.CONFIGURATION_TAG);
      configuration.writeExternal(config);
      return config;
    }
  }
}
