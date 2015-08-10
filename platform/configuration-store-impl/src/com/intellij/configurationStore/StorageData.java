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
package com.intellij.configurationStore;

import com.intellij.openapi.components.PathMacroSubstitutor;
import com.intellij.openapi.components.impl.stores.StateMap;
import com.intellij.openapi.components.impl.stores.StorageDataBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.SmartHashSet;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.openapi.components.impl.stores.StateMap.getNewByteIfDiffers;

public class StorageData extends StorageDataBase {
  private static final Logger LOG = Logger.getInstance(StorageData.class);

  private final StateMap myStates;

  public boolean isDirty() {
    return false;
  }

  public StorageData() {
    myStates = new StateMap();
  }

  protected StorageData(@NotNull StorageData storageData) {
    myStates = new StateMap(storageData.myStates);
  }

  @Override
  @NotNull
  public Set<String> getComponentNames() {
    return myStates.keys();
  }

  public void load(@NotNull Element rootElement, @Nullable PathMacroSubstitutor pathMacroSubstitutor, boolean intern) {
    load(myStates, rootElement, pathMacroSubstitutor, intern);
  }

  @Nullable
  public Element save(@NotNull Map<String, Element> newLiveStates, @NotNull String rootElementName) throws IOException {
    if (myStates.isEmpty()) {
      return null;
    }

    Element rootElement = new Element(rootElementName);
    String[] componentNames = ArrayUtil.toStringArray(myStates.keys());
    Arrays.sort(componentNames);
    for (String componentName : componentNames) {
      assert componentName != null;
      Element element = myStates.getElement(componentName, newLiveStates);
      // name attribute should be first
      List<Attribute> elementAttributes = element.getAttributes();
      if (elementAttributes.isEmpty()) {
        element.setAttribute(NAME, componentName);
      }
      else {
        Attribute nameAttribute = element.getAttribute(NAME);
        if (nameAttribute == null) {
          nameAttribute = new Attribute(NAME, componentName);
          elementAttributes.add(0, nameAttribute);
        }
        else {
          nameAttribute.setValue(componentName);
          if (elementAttributes.get(0) != nameAttribute) {
            elementAttributes.remove(nameAttribute);
            elementAttributes.add(0, nameAttribute);
          }
        }
      }

      rootElement.addContent(element);
    }
    return rootElement;
  }

  @Nullable
  public Element getState(@NotNull String name) {
    return myStates.getState(name);
  }

  @Nullable
  public Element getStateAndArchive(@NotNull String name) {
    return myStates.getStateAndArchive(name);
  }

  @Nullable
  public static StorageData setStateAndCloneIfNeed(@NotNull String componentName, @Nullable Element newState, @NotNull StorageData storageData, @NotNull Map<String, Element> newLiveStates) {
    Object oldState = storageData.myStates.get(componentName);
    if (newState == null || JDOMUtil.isEmpty(newState)) {
      if (oldState == null) {
        return null;
      }

      StorageData newStorageData = storageData.clone();
      newStorageData.myStates.remove(componentName);
      return newStorageData;
    }

    prepareElement(newState);

    newLiveStates.put(componentName, newState);

    byte[] newBytes = null;
    if (oldState instanceof Element) {
      if (JDOMUtil.areElementsEqual((Element)oldState, newState)) {
        return null;
      }
    }
    else if (oldState != null) {
      newBytes = getNewByteIfDiffers(componentName, newState, (byte[])oldState);
      if (newBytes == null) {
        return null;
      }
    }

    StorageData newStorageData = storageData.clone();
    newStorageData.myStates.put(componentName, newBytes == null ? newState : newBytes);
    return newStorageData;
  }

  @Nullable
  public final Object setState(@NotNull String componentName, @Nullable Element newState, @NotNull Map<String, Element> newLiveStates) {
    if (newState == null || JDOMUtil.isEmpty(newState)) {
      return myStates.remove(componentName);
    }

    prepareElement(newState);

    newLiveStates.put(componentName, newState);

    Object oldState = myStates.get(componentName);

    byte[] newBytes = null;
    if (oldState instanceof Element) {
      if (JDOMUtil.areElementsEqual((Element)oldState, newState)) {
        return null;
      }
    }
    else if (oldState != null) {
      newBytes = getNewByteIfDiffers(componentName, newState, (byte[])oldState);
      if (newBytes == null) {
        return null;
      }
    }

    myStates.put(componentName, newBytes == null ? newState : newBytes);
    return newState;
  }

  private static void prepareElement(@NotNull Element state) {
    if (state.getParent() != null) {
      LOG.warn("State element must not have parent " + JDOMUtil.writeElement(state));
      state.detach();
    }
    state.setName(COMPONENT);
  }

  @Override
  public StorageData clone() {
    return new StorageData(this);
  }

  // newStorageData - myStates contains only live (unarchived) states
  public Set<String> getChangedComponentNames(@NotNull StorageData newStorageData, @Nullable PathMacroSubstitutor substitutor) {
    Set<String> bothStates = new SmartHashSet<String>(myStates.keys());
    bothStates.retainAll(newStorageData.myStates.keys());

    Set<String> diffs = new SmartHashSet<String>();
    diffs.addAll(newStorageData.myStates.keys());
    diffs.addAll(myStates.keys());
    diffs.removeAll(bothStates);

    for (String componentName : bothStates) {
      myStates.compare(componentName, newStorageData.myStates, diffs);
    }
    return diffs;
  }

  @Override
  public boolean hasState(@NotNull String componentName) {
    return myStates.hasState(componentName);
  }
}
