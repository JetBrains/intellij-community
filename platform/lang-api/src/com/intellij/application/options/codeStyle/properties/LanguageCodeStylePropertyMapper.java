// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.application.options.IndentOptionsEditor;
import com.intellij.application.options.SmartIndentOptionsEditorBase;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.*;

import static com.intellij.application.options.codeStyle.properties.OverrideLanguageIndentOptionsAccessor.OVERRIDE_LANGUAGE_INDENT_OPTIONS_PROPERTY_NAME;

@ApiStatus.Experimental
public final class LanguageCodeStylePropertyMapper extends AbstractCodeStylePropertyMapper {
  private final @NotNull Language myLanguage;
  private final @NotNull String myLanguageDomainId;
  private final @Nullable LanguageCodeStyleSettingsProvider mySettingsProvider;
  private final @NotNull List<CustomCodeStyleSettings> myCustomSettings;

  public LanguageCodeStylePropertyMapper(@NotNull CodeStyleSettings settings,
                                         @NotNull Language language,
                                         @Nullable String languageDomainId) {
    super(settings);
    myLanguage = language;
    myLanguageDomainId = languageDomainId == null ? StringUtil.toLowerCase(myLanguage.getID()) : languageDomainId;
    mySettingsProvider = LanguageCodeStyleSettingsProvider.forLanguage(language);
    myCustomSettings = getCustomSettings();
  }

  @Override
  protected @Nullable CodeStylePropertyAccessor<?> getAccessor(@NotNull Object codeStyleObject, @NotNull Field field) {
    CodeStylePropertyAccessor<?> accessor = mySettingsProvider != null ? mySettingsProvider.getAccessor(codeStyleObject, field) : null;
    if (accessor != null) {
      return accessor;
    }
    return super.getAccessor(codeStyleObject, field);
  }

  @Override
  protected void addAdditionalAccessors(@NotNull Map<String, CodeStylePropertyAccessor<?>> accessorMap) {
    accessorMap.put(VisualGuidesAccessor.VISUAL_GUIDES_PROPERTY_NAME, new VisualGuidesAccessor(getRootSettings(), myLanguage));
    if (mySettingsProvider != null) {
      for (CustomCodeStyleSettings customSettings :  myCustomSettings) {
        for (CodeStylePropertyAccessor<?> accessor : mySettingsProvider.getAdditionalAccessors(customSettings)) {
          accessorMap.put(accessor.getPropertyName(), accessor);
        }
      }
    }
    IndentOptions indentOptions = getRootSettings().getCommonSettings(myLanguage).getIndentOptions();
    if (indentOptions != null) {
      accessorMap.put(OVERRIDE_LANGUAGE_INDENT_OPTIONS_PROPERTY_NAME, new OverrideLanguageIndentOptionsAccessor(indentOptions));
    }
  }

  @Override
  protected @NotNull List<CodeStyleObjectDescriptor> getSupportedFields() {
    List<CodeStyleObjectDescriptor> fieldsDescriptors = new ArrayList<>();
    IndentOptions indentOptions = getRootSettings().getCommonSettings(myLanguage).getIndentOptions();
    if (indentOptions != null) {
      fieldsDescriptors.add(new CodeStyleObjectDescriptor(indentOptions, getSupportedIndentOptions()));
    }
    fieldsDescriptors.add(new CodeStyleObjectDescriptor(getRootSettings().getCommonSettings(myLanguage), getSupportedLanguageFields()));
    for (CustomCodeStyleSettings customSettings : myCustomSettings) {
      fieldsDescriptors.add(new CodeStyleObjectDescriptor(customSettings, null));
    }
    return fieldsDescriptors;
  }

  public @NotNull Language getLanguage() {
    return myLanguage;
  }

  @Override
  public @NotNull String getLanguageDomainId() {
    return myLanguageDomainId;
  }

  private List<CustomCodeStyleSettings> getCustomSettings() {
    List<CustomCodeStyleSettings> customSettingsList = new ArrayList<>();
    addCustomSettings(customSettingsList, getRootSettings(), CodeStyleSettingsProvider.EXTENSION_POINT_NAME.getExtensionList());
    addCustomSettings(customSettingsList, getRootSettings(), new ArrayList<>(LanguageCodeStyleSettingsProvider.getSettingsPagesProviders()));
    return customSettingsList;
  }

  private void addCustomSettings(@NotNull List<? super CustomCodeStyleSettings> list,
                                 @NotNull CodeStyleSettings rootSettings,
                                 @NotNull List<? extends CodeStyleSettingsProvider> providerList) {
    for (CodeStyleSettingsProvider provider : providerList) {
      if (provider.getLanguage() == myLanguage && isEnabled(provider)) {
        CustomCodeStyleSettings customSettingsTemplate = provider.createCustomSettings(rootSettings);
        if (customSettingsTemplate != null) {
          CustomCodeStyleSettings customSettings = rootSettings.getCustomSettings(customSettingsTemplate.getClass());
          list.add(customSettings);
        }
      }
    }
  }

  private static boolean isEnabled(@NotNull CodeStyleSettingsProvider provider) {
    // Enable only providers defining a main language configurable in unit test mode, skip any secondary contributors
    // to avoid test flickering on different class paths.
    return !ApplicationManager.getApplication().isUnitTestMode() || provider.hasSettingsPage();
  }

  private Set<String> getSupportedIndentOptions() {
    LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(myLanguage);
    if (provider == null) return Collections.emptySet();
    Set<String> indentOptions =
      new HashSet<>(provider.getSupportedFields(LanguageCodeStyleSettingsProvider.SettingsType.INDENT_SETTINGS));
    if (indentOptions.isEmpty()) {
      IndentOptionsEditor editor = provider.getIndentOptionsEditor();
      if (editor != null) {
        indentOptions.add("TAB_SIZE");
        indentOptions.add("USE_TAB_CHARACTER");
        indentOptions.add("INDENT_SIZE");
        if (editor instanceof SmartIndentOptionsEditorBase) {
          indentOptions.add("CONTINUATION_INDENT_SIZE");
          indentOptions.add("SMART_TABS");
          indentOptions.add("KEEP_INDENTS_ON_EMPTY_LINES");
        }
      }
    }
    return indentOptions;
  }

  private Set<String> getSupportedLanguageFields() {
    return mySettingsProvider == null ? Collections.emptySet() : mySettingsProvider.getSupportedFields();
  }

  @Override
  public @Nullable String getPropertyDescription(@NotNull String externalName) {
    String key = "codestyle.property.description." + externalName;
    return OptionsBundle.INSTANCE.containsKey(key) ? OptionsBundle.message(key) : null;
  }
}
