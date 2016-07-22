/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.config;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.HashMap;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * @deprecated Use {@link PropertiesComponent} directly.
 * @see com.intellij.ui.JBSplitter
 */
@Deprecated
public interface Storage {
  void put(String key, String value);
  String get(String key);

  class PropertiesComponentStorage implements Storage {
    private static final Logger LOG = Logger.getInstance("#com.intellij.util.config.Storage.PropertiesComponentStorage");

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

  class MapStorage implements Storage {
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
