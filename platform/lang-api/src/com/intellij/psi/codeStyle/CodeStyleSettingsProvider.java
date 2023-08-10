// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.lang.IdeLanguageCustomization;
import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Base class which serves as a factory for custom code style settings and a settings page (configurable). In most cases language
 * settings and the configurable are defined in {@link LanguageCodeStyleSettingsProvider}. This class can be extended directly to contribute
 * to already existing settings page. In this case {@link #hasSettingsPage()} may return false not to create an extra node (configurable)
 * in the settings tree.
 */
public abstract class CodeStyleSettingsProvider implements CustomCodeStyleSettingsFactory, DisplayPrioritySortable {
  public static final ExtensionPointName<CodeStyleSettingsProvider> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.codeStyleSettingsProvider");


  /**
   * Create an object with custom code style settings.
   * @param settings The root settings (container).
   * @return The custom settings object.
   */
  @Override
  @Nullable
  public CustomCodeStyleSettings createCustomSettings(@NotNull CodeStyleSettings settings) {
    return null;
  }

  /**
   * @deprecated use {@link #createConfigurable(CodeStyleSettings, CodeStyleSettings)} or
   * {@link LanguageCodeStyleSettingsProvider#createConfigurable(CodeStyleSettings, CodeStyleSettings)} for language settings.
   */
  @Deprecated
  @NotNull
  public Configurable createSettingsPage(@NotNull CodeStyleSettings settings, @NotNull CodeStyleSettings modelSettings) {
    //noinspection ConstantConditions
    return null;
  }

  /**
   * Creates a code style configurable. The configurable uses original settings which are eventually changed after a user applies changes
   * and model settings which are immediately modified upon changes in UI.
   *
   * @param settings      The original settings.
   * @param modelSettings The model settings.
   *
   * @return The created code style configurable.
   */
  @NotNull
  public CodeStyleConfigurable createConfigurable(@NotNull CodeStyleSettings settings, @NotNull CodeStyleSettings modelSettings) {
    Configurable configurable = createSettingsPage(settings, modelSettings);
    if (configurable instanceof CodeStyleConfigurable) {
      return (CodeStyleConfigurable)configurable;
    }
    else {
      return new LegacyConfigurableWrapper(configurable);
    }
  }

  /**
   * Returns the name of the configurable page without creating a Configurable instance.
   *
   * @return the display name of the configurable page.
   */
  @Nullable
  public @NlsContexts.ConfigurableName String getConfigurableDisplayName() {
    Language lang = getLanguage();
    return lang == null ? null : lang.getDisplayName();
  }

  /**
   * @return True if a separate node must be created in the settings tree, false if created configurable will be used in already
   * existing settings page.
   */
  public boolean hasSettingsPage() {
    return true;
  }

  @Override
  public DisplayPriority getPriority() {
    List<Language> primaryIdeLanguages = IdeLanguageCustomization.getInstance().getPrimaryIdeLanguages();
    return primaryIdeLanguages.contains(getLanguage()) ? DisplayPriority.KEY_LANGUAGE_SETTINGS : DisplayPriority.LANGUAGE_SETTINGS;
  }

  /**
   * Specifies a language this provider applies to. If the language is not null, its display name will
   * be used as a configurable name by default if {@code getConfigurableDisplayName()} is not
   * overridden.
   *
   * @return null by default.
   */
  @Nullable
  public Language getLanguage() {
    return null;
  }

  /**
   * @return {@code CodeStyleGroup} instance if a configurable returned by {@link #createConfigurable(CodeStyleSettings, CodeStyleSettings)}
   *         is a part of a code style group or {@code null} if the configurable must be shown directly under "Code Style" settings node.
   * @see CodeStyleGroup
   */
  @Nullable
  public CodeStyleGroup getGroup() {
    return null;
  }

  private static final class LegacyConfigurableWrapper implements CodeStyleConfigurable {
    @NotNull
    private final Configurable myConfigurable;

    private LegacyConfigurableWrapper(@NotNull Configurable configurable) {
      myConfigurable = configurable;
    }

    @Override
    public void reset(@NotNull CodeStyleSettings settings) {
      myConfigurable.reset();
    }

    @Override
    public void apply(@NotNull CodeStyleSettings settings) throws ConfigurationException {
      myConfigurable.apply();
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
      return myConfigurable.getDisplayName();
    }

    @Nullable
    @Override
    public JComponent createComponent() {
      return myConfigurable.createComponent();
    }

    @Override
    public boolean isModified() {
      return myConfigurable.isModified();
    }

    @Override
    public void apply() throws ConfigurationException {
      myConfigurable.apply();
    }
  }
}
