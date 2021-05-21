// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.containers.ClassMap;
import com.intellij.util.containers.JBIterable;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class CustomCodeStyleSettingsManager {


  private final ClassMap<CustomCodeStyleSettings> myCustomSettings = new ClassMap<>();
  private final CodeStyleSettings myRootSettings;
  private final Map<String, Element> myUnknownCustomElements = new HashMap<>();

  CustomCodeStyleSettingsManager(@NotNull CodeStyleSettings settings) {
    myRootSettings = settings;
  }


  void addCustomSettings(@Nullable CustomCodeStyleSettings settings) {
    if (settings != null) {
      synchronized (myCustomSettings) {
        myCustomSettings.put(settings.getClass(), settings);
      }
    }
  }

  @NotNull
  <T extends CustomCodeStyleSettings> T getCustomSettings(@NotNull Class<T> aClass) {
    synchronized (myCustomSettings) {
      //noinspection unchecked
      T result = (T)myCustomSettings.get(aClass);
      if (result == null) {
        throw new RuntimeException("Unable to get registered settings of #" + aClass.getSimpleName() + " (" + aClass.getName() + ")");
      }
      return result;
    }
  }

  @Nullable
  <T extends CustomCodeStyleSettings> T getCustomSettingsIfCreated(@NotNull Class<T> aClass) {
    synchronized (myCustomSettings) {
      //noinspection unchecked
      return (T)myCustomSettings.get(aClass);
    }
  }


  void registerCustomSettings(@NotNull CodeStyleSettings rootSettings, @NotNull CustomCodeStyleSettingsFactory factory) {
    CustomCodeStyleSettings customSettings = factory.createCustomSettings(rootSettings);
    if (customSettings != null) {
      for (String tagName : customSettings.getKnownTagNames()) {
        if (myUnknownCustomElements.containsKey(tagName)) {
          restoreCustomSettings(customSettings);
          return;
        }
      }
    }
  }

  private void restoreCustomSettings(@NotNull CustomCodeStyleSettings customSettings) {
    Element tempElement = new Element("temp");
    for (String tagName : customSettings.getKnownTagNames()) {
      Element unknown = myUnknownCustomElements.get(tagName);
      if (unknown != null) {
        tempElement.addContent(unknown.clone());
      }
    }
    customSettings.readExternal(tempElement);
    synchronized (myCustomSettings) {
      myCustomSettings.put(customSettings.getClass(), customSettings);
      customSettings.getKnownTagNames().forEach(myUnknownCustomElements::remove);
    }
  }

  public void unregisterCustomSettings(@NotNull CustomCodeStyleSettingsFactory factory) {
    CustomCodeStyleSettings defaultSettings = factory.createCustomSettings(CodeStyleSettings.getDefaults());
    if (defaultSettings != null) {
      synchronized (myCustomSettings) {
        CustomCodeStyleSettings customSettings = myCustomSettings.get(defaultSettings.getClass());
        if (customSettings != null) {
          Element tempElement = new Element("temp");
          customSettings.writeExternal(tempElement, defaultSettings);
          for (Element child : tempElement.getChildren()) {
            myUnknownCustomElements.put(child.getName(), JDOMUtil.internElement(child));
          }
          myCustomSettings.remove(customSettings.getClass());
        }
      }
    }
  }

  void copyFrom(@NotNull CodeStyleSettings source) {
    synchronized (myCustomSettings) {
      myCustomSettings.clear();
      for (final CustomCodeStyleSettings customSettings : source.getCustomSettingsValues()) {
        myCustomSettings.put(customSettings.getClass(), customSettings.copyWith(myRootSettings));
      }
    }
  }

  void notifySettingsBeforeLoading() {
    JBIterable.from(myCustomSettings.values())
      .forEach(CustomCodeStyleSettings::beforeLoading);
  }

  void notifySettingsLoaded() {
    JBIterable.from(myCustomSettings.values())
      .forEach(CustomCodeStyleSettings::afterLoaded);
  }

  Collection<CustomCodeStyleSettings> getAllSettings() {
    synchronized (myCustomSettings) {
      return Collections.unmodifiableCollection(myCustomSettings.values());
    }
  }

  void readExternal(@NotNull Element element) {
    Set<String> knownTags = new HashSet<>();
    knownTags.add(CommonCodeStyleSettingsManager.COMMON_SETTINGS_TAG);
    knownTags.add(CodeStyleSettings.ADDITIONAL_INDENT_OPTIONS);
    knownTags.add("option");
    for (CustomCodeStyleSettings settings : getAllSettings()) {
      knownTags.addAll(settings.getKnownTagNames());
      settings.readExternal(element);
    }
    for (Element child : element.getChildren()) {
      String tag = child.getName();
      if (!knownTags.contains(tag)) {
        myUnknownCustomElements.put(tag, JDOMUtil.internElement(child));
      }
    }
  }

  void writeExternal(@NotNull Element element, @NotNull CodeStyleSettings defaultSettings) {
    List<String> tags = new ArrayList<>();
    Element tempRoot = new Element("temp");
    for (CustomCodeStyleSettings settings : getAllSettings()) {
      tags.addAll(settings.getKnownTagNames());
      settings.writeExternal(tempRoot, defaultSettings.getCustomSettings(settings.getClass()));
    }
    tags.addAll(myUnknownCustomElements.keySet());
    Collections.sort(tags);
    for (String tag : tags) {
      Element child = tempRoot.getChild(tag);
      if (child != null) {
        element.addContent(child.clone());
      }
      else if (myUnknownCustomElements.containsKey(tag)) {
        element.addContent(myUnknownCustomElements.get(tag).clone());
      }
    }
  }
}
