// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.JBIterable;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

class CustomCodeStyleSettingsManager {


  private final Map<String, CustomCodeStyleSettings> myCustomSettings = new HashMap<>();
  private final @NotNull CodeStyleSettings myRootSettings;
  private final Map<String, Element> myUnknownCustomElements = new HashMap<>();

  CustomCodeStyleSettingsManager(@NotNull CodeStyleSettings settings) {
    myRootSettings = settings;
  }

  void initCustomSettings() {
    for (final CustomCodeStyleSettingsFactory factory : CodeStyleSettingsService.getInstance().getCustomCodeStyleSettingsFactories()) {
      addCustomSettings(myRootSettings, factory);
    }
  }

  private void addCustomSettings(@NotNull CodeStyleSettings rootSettings, @NotNull CustomCodeStyleSettingsFactory factory) {
    CustomCodeStyleSettings customSettings = factory.createCustomSettings(rootSettings);
    if (customSettings != null) {
      synchronized (myCustomSettings) {
        myCustomSettings.put(customSettings.getClass().getName(), customSettings);
      }
    }
  }

  @NotNull
  <T extends CustomCodeStyleSettings> T getCustomSettings(@NotNull Class<T> aClass) {
    String className = aClass.getName();
    CustomCodeStyleSettings result;
    synchronized (myCustomSettings) {
      result = myCustomSettings.get(className);
    }
    if (result == null) {
      result = createCustomSettings(className);
      if (result != null) {
        registerCustomSettings(result);
      }
      else {
        throw new RuntimeException("Unable to get or create settings of #" + aClass.getSimpleName() + " (" + className + ")");
      }
    }
    //noinspection unchecked
    return (T)result;
  }

  @Nullable
  <T extends CustomCodeStyleSettings> T getCustomSettingsIfCreated(@NotNull Class<T> aClass) {
    synchronized (myCustomSettings) {
      //noinspection unchecked
      return (T)myCustomSettings.get(aClass.getName());
    }
  }


  private @Nullable CustomCodeStyleSettings createCustomSettings(@NotNull String customSettingsClassName) {
    for (final CustomCodeStyleSettingsFactory factory : CodeStyleSettingsService.getInstance().getCustomCodeStyleSettingsFactories()) {
      CustomCodeStyleSettings customSettings = factory.createCustomSettings(myRootSettings);
      if (customSettings != null && customSettingsClassName.equals(customSettings.getClass().getName())) {
        return customSettings;
      }
    }
    return null;
  }

  void registerCustomSettings(@NotNull CodeStyleSettings rootSettings, @NotNull CustomCodeStyleSettingsFactory factory) {
    CustomCodeStyleSettings customSettings = factory.createCustomSettings(rootSettings);
    if (customSettings != null) {
      registerCustomSettings(customSettings);
    }
  }

  private void registerCustomSettings(@NotNull CustomCodeStyleSettings customSettings) {
    for (String tagName : customSettings.getKnownTagNames()) {
      if (myUnknownCustomElements.containsKey(tagName)) {
        restoreCustomSettings(customSettings);
        return;
      }
    }
    synchronized (myCustomSettings) {
      myCustomSettings.put(customSettings.getClass().getName(), customSettings);
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
      myCustomSettings.put(customSettings.getClass().getName(), customSettings);
      customSettings.getKnownTagNames().forEach(myUnknownCustomElements::remove);
    }
  }

  public void unregisterCustomSettings(@NotNull CustomCodeStyleSettingsFactory factory) {
    CustomCodeStyleSettings defaultSettings = factory.createCustomSettings(CodeStyleSettings.getDefaults());
    if (defaultSettings != null) {
      synchronized (myCustomSettings) {
        CustomCodeStyleSettings customSettings = myCustomSettings.get(defaultSettings.getClass().getName());
        if (customSettings != null) {
          Element tempElement = new Element("temp");
          customSettings.writeExternal(tempElement, defaultSettings);
          for (Element child : tempElement.getChildren()) {
            myUnknownCustomElements.put(child.getName(), JDOMUtil.internElement(child));
          }
          myCustomSettings.remove(customSettings.getClass().getName());
        }
      }
    }
  }

  void copyFrom(@NotNull CodeStyleSettings source) {
    synchronized (myCustomSettings) {
      Pair<Collection<CustomCodeStyleSettings>,Map<String,Element>> maps = source.getCustomCodeStyleSettingsManager().getMaps();
      myCustomSettings.clear();
      for (CustomCodeStyleSettings customSettings : maps.first) {
        myCustomSettings.put(customSettings.getClass().getName(), customSettings.copyWith(myRootSettings));
      }
      for (String tagName : maps.second.keySet()) {
        myUnknownCustomElements.put(tagName, JDOMUtil.internElement(maps.second.get(tagName).clone()));
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

  @Unmodifiable
  Collection<CustomCodeStyleSettings> getAllSettings() {
    synchronized (myCustomSettings) {
      return Collections.unmodifiableCollection(myCustomSettings.values());
    }
  }

  private @NotNull Pair<Collection<CustomCodeStyleSettings>, Map<String,Element>> getMaps() {
    synchronized (myCustomSettings) {
      return Pair.create(
        new ArrayList<>(myCustomSettings.values()),
        new HashMap<>(myUnknownCustomElements)
      );
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
    Collection<CustomCodeStyleSettings> allSettings = getAllSettings();
    for (CustomCodeStyleSettings settings : allSettings) {
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
