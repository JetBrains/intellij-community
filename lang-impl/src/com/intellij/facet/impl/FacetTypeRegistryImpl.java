/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl;

import com.intellij.facet.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author nik
 */
public class FacetTypeRegistryImpl extends FacetTypeRegistry {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.FacetTypeRegistryImpl");
  private Map<String, FacetTypeId> myTypeIds = new HashMap<String, FacetTypeId>();
  private Map<FacetTypeId, FacetType> myFacetTypes = new HashMap<FacetTypeId, FacetType>();
  private boolean myExtensionsLoaded = false;

  public synchronized void registerFacetType(FacetType facetType) {
    final FacetTypeId typeId = facetType.getId();
    String id = facetType.getStringId();
    LOG.assertTrue(!id.contains("/"), "Facet type id '" + id + "' contains illegal character '/'");
    LOG.assertTrue(!myFacetTypes.containsKey(typeId), "Facet type '" + id + "' is already registered");
    myFacetTypes.put(typeId, facetType);

    LOG.assertTrue(!myTypeIds.containsKey(id), "Facet type id '" + id + "' is already registered");
    myTypeIds.put(id, typeId);
  }

  public synchronized void unregisterFacetType(FacetType facetType) {
    final FacetTypeId id = facetType.getId();
    final String stringId = facetType.getStringId();
    LOG.assertTrue(myFacetTypes.remove(id) != null, "Facet type '" + stringId + "' is not registered");
    myFacetTypes.remove(id);
    myTypeIds.remove(stringId);
  }

  public synchronized FacetTypeId[] getFacetTypeIds() {
    loadExtensions();
    final Set<FacetTypeId> ids = myFacetTypes.keySet();
    return ids.toArray(new FacetTypeId[ids.size()]);
  }

  public synchronized FacetType[] getFacetTypes() {
    loadExtensions();
    final Collection<FacetType> types = myFacetTypes.values();
    return types.toArray(new FacetType[types.size()]);
  }

  @Nullable
  public synchronized FacetType findFacetType(String id) {
    loadExtensions();
    final FacetTypeId typeId = myTypeIds.get(id);
    return typeId == null ? null : myFacetTypes.get(typeId);
  }

  @Nullable
  public synchronized <F extends Facet<C>, C extends FacetConfiguration> FacetType<F, C> findFacetType(FacetTypeId<F> typeId) {
    loadExtensions();
    return myFacetTypes.get(typeId);
  }

  private void loadExtensions() {
    if (!myExtensionsLoaded) {
      myExtensionsLoaded = true;
      final ExtensionPoint<FacetType> extensionPoint = Extensions.getArea(null).getExtensionPoint(FacetType.EP_NAME);
      extensionPoint.addExtensionPointListener(new ExtensionPointListener<FacetType>() {
        public void extensionAdded(final FacetType extension, @Nullable final PluginDescriptor pluginDescriptor) {
          registerFacetType(extension);
        }

        public void extensionRemoved(final FacetType extension, @Nullable final PluginDescriptor pluginDescriptor) {
          unregisterFacetType(extension);
        }
      });
    }
  }
}
