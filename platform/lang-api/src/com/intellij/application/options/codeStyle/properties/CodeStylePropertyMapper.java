// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.application.options.IndentOptionsEditor;
import com.intellij.application.options.SmartIndentOptionsEditor;
import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@ApiStatus.Experimental
public class CodeStylePropertyMapper {
  private @NotNull final CodeStyleSettings myRootSettings;
  private @NotNull final Language myLanguage;
  private final Map<String, CodeStylePropertyAccessor> myAccessorMap = ContainerUtil.newHashMap();

  public CodeStylePropertyMapper(@NotNull CodeStyleSettings settings, @NotNull Language language) {
    myRootSettings = settings;
    myLanguage = language;
    fillMap();
  }

  public void setProperty(@NotNull String name, @NotNull String value) {
    if (myAccessorMap.containsKey(name)) {
      myAccessorMap.get(name).set(value);
    }
  }

  @Nullable
  public String getProperty(@NotNull String name) {
    if (myAccessorMap.containsKey(name)) {
      return myAccessorMap.get(name).get();
    }
    return null;
  }

  public List<String> enumProperties() {
    return myAccessorMap.keySet().stream().sorted().collect(Collectors.toList());
  }

  public List<String> enumPropertiesFor(@NotNull Class... codeStyleClass) {
    return myAccessorMap.keySet().stream().filter(name -> {
      CodeStylePropertyAccessor accessor = myAccessorMap.get(name);
      for (Class aClass : codeStyleClass) {
        if (accessor.getObjectClass().equals(aClass)) {
          return true;
        }
      }
      return false;
    }).sorted().collect(Collectors.toList());
  }

  private void fillMap() {
    CommonCodeStyleSettings.IndentOptions indentOptions = myRootSettings.getCommonSettings(myLanguage).getIndentOptions();
    if (indentOptions != null) {
      addAccessorsFor(indentOptions, getSupportedIndentOptions());
    }
    addAccessorsFor(myRootSettings.getCommonSettings(myLanguage), getSupportedFields());
    for (CustomCodeStyleSettings customSettings : getCustomSettings()) {
      addAccessorsFor(customSettings, null);
    }
  }

  private List<CustomCodeStyleSettings> getCustomSettings() {
    CodeStyleSettings rootSettings = new CodeStyleSettings();
    List<CustomCodeStyleSettings> customSettingsList = new ArrayList<>();
    addCustomSettings(customSettingsList, rootSettings, CodeStyleSettingsProvider.EXTENSION_POINT_NAME.getExtensionList());
    addCustomSettings(customSettingsList, rootSettings, LanguageCodeStyleSettingsProvider.getSettingsPagesProviders());
    return customSettingsList;
  }

  private void addCustomSettings(@NotNull List<CustomCodeStyleSettings> list,
                                 @NotNull CodeStyleSettings rootSettings,
                                 @NotNull List<? extends CodeStyleSettingsProvider> providerList) {
    for (CodeStyleSettingsProvider provider : providerList) {
      if (provider.getLanguage() == myLanguage) {
        CustomCodeStyleSettings customSettings = provider.createCustomSettings(rootSettings);
        if (customSettings != null) {
          list.add(customSettings);
        }
      }
    }
  }

  private Set<String> getSupportedIndentOptions() {
    LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(myLanguage);
    if (provider == null) return Collections.emptySet();
    Set<String> indentOptions = ContainerUtil.newHashSet();
    IndentOptionsEditor editor = provider.getIndentOptionsEditor();
    if (editor != null) {
      indentOptions.add("TAB_SIZE");
      indentOptions.add("USE_TAB_CHARACTER");
      indentOptions.add("INDENT_SIZE");
      if (editor instanceof SmartIndentOptionsEditor) {
        indentOptions.add("CONTINUATION_INDENT_SIZE");
        indentOptions.add("SMART_TABS");
        indentOptions.add("KEEP_INDENTS_ON_EMPTY_LINES");
      }
    }
    return indentOptions;
  }

  private Set<String> getSupportedFields() {
    LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(myLanguage);
    return provider == null ? Collections.emptySet() : provider.getSupportedFields();
  }

  private void addAccessorsFor(@NotNull Object codeStyleObject, @Nullable Set<String> supportedFields) {
    Class codeStyleClass = codeStyleObject.getClass();
    for (Field field : getCodeStyleFields(codeStyleClass)) {
      String fieldName = field.getName();
      if (supportedFields == null || supportedFields.contains(fieldName)) {
        final CodeStylePropertyAccessor accessor = new PropertyAccessorFactory(field).createAccessor(codeStyleObject);
        if (accessor != null) {
          myAccessorMap.put(PropertyNameUtil.getPropertyName(fieldName), accessor);
        }
      }
    }
  }

  private static List<Field> getCodeStyleFields(Class codeStyleClass) {
    List<Field> fields = new ArrayList<>();
    for (Field field : codeStyleClass.getFields()) {
      if (isPublic(field) && !isFinal(field)) {
        fields.add(field);
      }
    }
    return fields;
  }

  private static boolean isPublic(final Field field) {
    return (field.getModifiers() & Modifier.PUBLIC) != 0;
  }

  private static boolean isFinal(final Field field) {
    return (field.getModifiers() & Modifier.FINAL) != 0;
  }

}
