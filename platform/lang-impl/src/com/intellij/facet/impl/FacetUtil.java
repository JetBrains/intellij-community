/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl;

import com.intellij.facet.*;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.TypeVariable;
import java.util.Arrays;

/**
 * @author nik
 */
public class FacetUtil {
  private FacetUtil() {
  }

  public static void deleteFacet(final Facet facet) {
    new WriteAction() {
      protected void run(final Result result) {
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
        TypeVariable<Class<PersistentStateComponent>> variable = PersistentStateComponent.class.getTypeParameters()[0];
        Class<?> stateClass = ReflectionUtil.getRawType(ReflectionUtil.resolveVariableInHierarchy(variable, configuration.getClass()));
        ((PersistentStateComponent)configuration).loadState(XmlSerializer.deserialize(config, stateClass));
      }
      else {
        configuration.readExternal(config);
      }
    }
  }

  public static Element saveFacetConfiguration(final FacetConfiguration configuration) throws WriteExternalException {
    if (configuration instanceof PersistentStateComponent) {
      Object state = ((PersistentStateComponent)configuration).getState();
      return XmlSerializer.serialize(state, new SkipDefaultValuesSerializationFilters());
    }
    else {
      final Element config = new Element(FacetManagerImpl.CONFIGURATION_ELEMENT);
      configuration.writeExternal(config);
      return config;
    }
  }
}
