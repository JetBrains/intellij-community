// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.application.options.IndentOptionsEditor;
import com.intellij.application.options.SmartIndentOptionsEditor;
import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@ApiStatus.Experimental
public class LanguageCodeStylePropertyMapper extends AbstractCodeStylePropertyMapper {
  private @NotNull final Language myLanguage;

  public LanguageCodeStylePropertyMapper(@NotNull CodeStyleSettings settings, @NotNull Language language) {
    super(settings);
    myLanguage = language;
   }

  @NotNull
  @Override
  protected List<CodeStyleObjectDescriptor> getSupportedFields() {
    List<CodeStyleObjectDescriptor> fieldsDescriptors = ContainerUtil.newArrayList();
    IndentOptions indentOptions = getRootSettings().getCommonSettings(myLanguage).getIndentOptions();
    if (indentOptions != null) {
      fieldsDescriptors.add(new CodeStyleObjectDescriptor(indentOptions, getSupportedIndentOptions()));
    }
    fieldsDescriptors.add(new CodeStyleObjectDescriptor(getRootSettings().getCommonSettings(myLanguage), getSupportedLanguageFields()));
    for (CustomCodeStyleSettings customSettings : getCustomSettings()) {
      fieldsDescriptors.add(new CodeStyleObjectDescriptor(customSettings, null));
    }
    return fieldsDescriptors;
  }

  @NotNull
  @Override
  public String getLanguageDomainId() {
    return myLanguage.getID().toLowerCase(Locale.ENGLISH);
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

  private Set<String> getSupportedLanguageFields() {
    LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(myLanguage);
    return provider == null ? Collections.emptySet() : provider.getSupportedFields();
  }

}
