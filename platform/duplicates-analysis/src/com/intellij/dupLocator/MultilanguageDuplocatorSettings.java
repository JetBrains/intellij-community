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
package com.intellij.dupLocator;

import com.intellij.lang.Language;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.JDOMUtil;
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
  private final Map<String, ExternalizableDuplocatorState> mySettingsMap = new TreeMap<>();

  public static MultilanguageDuplocatorSettings getInstance() {
    return ServiceManager.getService(MultilanguageDuplocatorSettings.class);
  }

  public void registerState(@NotNull Language language, @NotNull ExternalizableDuplocatorState state) {
    synchronized (mySettingsMap) {
      mySettingsMap.put(language.getDisplayName(), state);
    }
  }

  public ExternalizableDuplocatorState getState(@NotNull Language language) {
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
        Element child = XmlSerializer.serialize(mySettingsMap.get(name), filter);
        if (!JDOMUtil.isEmpty(child)) {
          child.setName("object");
          child.setAttribute("language", name);
          state.addContent(child);
        }
      }
      return state;
    }
  }

  @Override
  public void loadState(Element state) {
    synchronized (mySettingsMap) {
      if (state == null) {
        return;
      }

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
