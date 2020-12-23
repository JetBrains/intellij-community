// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dupLocator;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.TreeMap;

/**
 * @author Eugene.Kudelevsky
 */
@State(
  name = "MultiLanguageDuplocatorSettings",
  storages = @Storage("duplocatorSettings.xml")
)
public class MultilanguageDuplocatorSettings implements PersistentStateComponent<Element> {
  private final Map<String, DefaultDuplocatorState> mySettingsMap = new TreeMap<>();

  public static MultilanguageDuplocatorSettings getInstance() {
    return ApplicationManager.getApplication().getService(MultilanguageDuplocatorSettings.class);
  }

  public void registerState(@NotNull Language language, @NotNull DefaultDuplocatorState state) {
    synchronized (mySettingsMap) {
      mySettingsMap.put(language.getDisplayName(), state);
    }
  }

  public DefaultDuplocatorState getState(@NotNull Language language) {
    synchronized (mySettingsMap) {
      return mySettingsMap.get(language.getDisplayName());
    }
  }

  @Override
  public Element getState() {
    synchronized (mySettingsMap) {
      Element state = new Element("state");
      if (mySettingsMap.isEmpty()) {
        return state;
      }

      SkipDefaultValuesSerializationFilters filter = new SkipDefaultValuesSerializationFilters();
      for (String name : mySettingsMap.keySet()) {
        Element child = XmlSerializer.serializeIfNotDefault(mySettingsMap.get(name), filter);
        if (child != null) {
          child.setName("object");
          child.setAttribute("language", name);
          state.addContent(child);
        }
      }
      return state;
    }
  }

  @Override
  public void loadState(@NotNull Element state) {
    synchronized (mySettingsMap) {
      for (Element objectElement : state.getChildren("object")) {
        String language = objectElement.getAttributeValue("language");
        if (language != null) {
          ExternalizableDuplocatorState stateObject = mySettingsMap.get(language);
          if (stateObject != null) {
            XmlSerializer.deserializeInto(stateObject, objectElement);
          }
        }
      }
    }
  }
}
