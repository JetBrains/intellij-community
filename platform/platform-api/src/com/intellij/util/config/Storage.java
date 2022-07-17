// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.config;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @deprecated Use {@link PropertiesComponent} directly.
 * @see com.intellij.ui.JBSplitter
 */
@Deprecated(forRemoval = true)
public interface Storage {
  void put(String key, String value);
  String get(String key);

  final class PropertiesComponentStorage implements Storage {
    private static final Logger LOG = Logger.getInstance(PropertiesComponentStorage.class);

    private final PropertiesComponent myPropertiesComponent;
    private final String myPrefix;

    public PropertiesComponentStorage(String prefix, PropertiesComponent propertiesComponent) {
      LOG.assertTrue(propertiesComponent != null || ApplicationManager.getApplication().isUnitTestMode());
      myPropertiesComponent = propertiesComponent;
      myPrefix = prefix;
    }

    public PropertiesComponentStorage(String prefix) {
      this(prefix, PropertiesComponent.getInstance());
    }

    @Override
    public void put(String key, String value) {
      if (myPropertiesComponent != null) {
        myPropertiesComponent.setValue(myPrefix + key, value);
      }
    }

    @Override
    public String get(String key) {
      return myPropertiesComponent != null ? myPropertiesComponent.getValue(myPrefix + key) : null;
    }

    @Override
    public String toString() {
      return "PropertiesComponentStorage: " + myPrefix;
    }
  }

  final class MapStorage implements Storage {
    private final Map<String, String> myValues = new HashMap<>();

    @Override
    public String get(String key) {
      return myValues.get(key);
    }

    @Override
    public void put(String key, String value) {
      myValues.put(key, value);
    }

    public Iterator<String> getKeys() {
      return Collections.unmodifiableCollection(myValues.keySet()).iterator();
    }
  }
}
