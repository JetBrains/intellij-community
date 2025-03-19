// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.impl;

import com.intellij.configurationStore.ComponentSerializationUtil;
import com.intellij.configurationStore.XmlSerializer;
import com.intellij.facet.*;
import com.intellij.facet.impl.invalid.InvalidFacet;
import com.intellij.facet.impl.invalid.InvalidFacetConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetManagerBridge;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.facet.JpsFacetSerializer;

import java.util.Arrays;

public class FacetUtil {

  public static <F extends Facet> F addFacet(Module module, FacetType<F, ?> type) {
    return addFacet(module, type, type.getPresentableName());
  }

  public static <F extends Facet> F addFacet(@NotNull Module module, @NotNull FacetType<F, ?> type, @NotNull String facetName) {
    final ModifiableFacetModel model = FacetManager.getInstance(module).createModifiableModel();
    final F facet = createFacet(module, type, facetName);
    ApplicationManager.getApplication().runWriteAction(() -> {
      model.addFacet(facet);
      model.commit();
    });
    return facet;
  }

  private static <F extends Facet, C extends FacetConfiguration> F createFacet(final Module module,
                                                                               final FacetType<F, C> type, @NotNull String facetName) {
    return FacetManager.getInstance(module).createFacet(type, facetName, type.createDefaultConfiguration(), null);
  }

  public static void deleteFacet(final Facet facet) {
    WriteAction.runAndWait(() -> {
      if (!isRegistered(facet)) {
        return;
      }

      ModifiableFacetModel model = FacetManager.getInstance(facet.getModule()).createModifiableModel();
      model.removeFacet(facet);
      model.commit();
    });
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

  public static @Nullable Element saveFacetConfiguration(@NotNull FacetConfiguration configuration) {
    if (configuration instanceof PersistentStateComponent) {
      Object state = ((PersistentStateComponent<?>)configuration).getState();
      if (state instanceof Element) {
        return ((Element)state);
      }
      else {
        Element result = XmlSerializer.serialize(state);
        return result == null ? new Element(JpsFacetSerializer.CONFIGURATION_TAG) : result;
      }
    }
    else if (configuration instanceof InvalidFacetConfiguration) {
      return ((InvalidFacetConfiguration)configuration).getFacetState().getConfiguration();
    }
    else {
      final Element config = new Element(JpsFacetSerializer.CONFIGURATION_TAG);
      configuration.writeExternal(config);
      return config;
    }
  }

  @ApiStatus.Internal
  public static @Nullable Element saveFacetConfiguration(Facet<?> facet) {
    final Element config;
    try {
      FacetConfiguration configuration = facet.getConfiguration();
      config = saveFacetConfiguration(configuration);
      if (facet instanceof JDOMExternalizable) {
        ((JDOMExternalizable)facet).writeExternal(config);
      }
    }
    catch (WriteExternalException e) {
      return null;
    }
    return config;
  }

  @SuppressWarnings("unchecked")
  static Facet<?> createFacetFromStateRawJ(@NotNull Module module, @NotNull FacetType<?, ?> facetType, @NotNull InvalidFacet invalidFacet) {
    return FacetManagerBridge.Companion.createFacetFromStateRaw$intellij_platform_lang_impl(
      module, facetType,
      invalidFacet.getConfiguration().getFacetState(),
      invalidFacet.getUnderlyingFacet()
    );
  }
}
