package com.intellij.util.config;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.HashMap;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

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

    public void put(String key, String value) {
      if (myPropertiesComponent != null)
        myPropertiesComponent.setValue(myPrefix + key, value);
    }

    public String get(String key) {
      return myPropertiesComponent != null ? myPropertiesComponent.getValue(myPrefix + key) : null;
    }

    public String toString() {
      return "PropertiesComponentStorage: " + myPrefix;
    }
  }

  class MapStorage implements Storage {
    private final Map<String, String> myValues = new HashMap<String, String>();
    public String get(String key) {
      return myValues.get(key);
    }

    public void put(String key, String value) {
      myValues.put(key, value);
    }

    public Iterator<String> getKeys() {
      return Collections.unmodifiableCollection(myValues.keySet()).iterator();
    }
  }
}
