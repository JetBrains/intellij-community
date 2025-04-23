// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.application.options.IndentOptionsEditor;
import com.intellij.application.options.codeStyle.properties.AbstractCodeStylePropertyMapper;
import com.intellij.application.options.codeStyle.properties.CodeStyleFieldAccessor;
import com.intellij.application.options.codeStyle.properties.CodeStylePropertyAccessor;
import com.intellij.application.options.codeStyle.properties.LanguageCodeStylePropertyMapper;
import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Base class and extension point for common code style settings for a specific language.
 */
public abstract class LanguageCodeStyleSettingsProvider extends CodeStyleSettingsProvider implements LanguageCodeStyleProvider {
  public static final ExtensionPointName<LanguageCodeStyleSettingsProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.langCodeStyleSettingsProvider");

  public enum SettingsType {
    BLANK_LINES_SETTINGS, SPACING_SETTINGS, WRAPPING_AND_BRACES_SETTINGS, INDENT_SETTINGS, COMMENTER_SETTINGS, LANGUAGE_SPECIFIC
  }

  public static @NotNull List<LanguageCodeStyleSettingsProvider> getAllProviders() {
    return LanguageCodeStyleSettingsProviderService.getInstance().getAllProviders();
  }

  public abstract @Nullable String getCodeSample(@NotNull SettingsType settingsType);

  public int getRightMargin(@NotNull SettingsType settingsType) {
    return settingsType == SettingsType.WRAPPING_AND_BRACES_SETTINGS ? 30 : -1;
  }

  /**
   * Customize settings in the given consumer. The consumer is, for example, a UI panel or a supported settings collector. Even if the
   * settings are not exposed to the UI via standard Spaces, Wrapping and Braces etc. panels, the method is still important for the platform
   * to collect settings from {@link CommonCodeStyleSettings} and {@link CommonCodeStyleSettings.IndentOptions} which the plug-in
   * supports. This information is used, for example, to generate language-specific {@code .editorconfig} properties.
   *
   * @param consumer Customized settings consumer.
   * @param settingsType The settings type.
   */
  public void customizeSettings(@NotNull CodeStyleSettingsCustomizable consumer, @NotNull SettingsType settingsType) {
  }

  public static @Nullable CommonCodeStyleSettings getDefaultCommonSettings(Language lang) {
    LanguageCodeStyleSettingsProvider provider = forLanguage(lang);
    return provider != null ? provider.getDefaultCommonSettings() : null;
  }

  /**
   * Override this method if file extension to be used with samples is different from the one returned by associated file type.
   *
   * @return The file extension for samples (null by default).
   */
  public @Nullable String getFileExt() {
    return null;
  }

  /**
   * Override this method if language name shown in preview tab must be different from the name returned by Language class itself.
   *
   * @return The language name to show in preview tab (null by default).
   */
  public @Nullable @NlsContexts.Label String getLanguageName() {
    return null;
  }

  /**
   * @return Language ID to be used in external formats like Json and .editorconfig. Must consist only of low case {@code 'a'...'z'} and
   * '-' characters. Base implementation uses {@link Language#getID()}, converts it to lower case letters and replaces any non-latin
   * characters with '-'.
   */
  public @NotNull String getExternalLanguageId() {
    return sanitizeLanguageId(StringUtil.toLowerCase(getLanguage().getID()));
  }

  private static String sanitizeLanguageId(@NotNull String id) {
    StringBuilder sb = new StringBuilder(id.length());
    id.chars().forEach(
      c-> {
        if (c >= 'a' && c <= 'z') {
          sb.appendCodePoint(c);
        }
        else {
          sb.append('-');
        }
      }
    );
    return sb.toString();
  }

  /**
   * Allows to customize PSI file creation for a language settings preview panel.
   * <p>
   * <b>IMPORTANT</b>: The created file must be a non-physical one with PSI events disabled. For more information see
   * {@link com.intellij.psi.PsiFileFactory#createFileFromText(String, Language, CharSequence, boolean, boolean)} where
   * {@code eventSystemEnabled} parameter must be {@code false}
   *
   * @param project current project
   * @param text    code sample to demonstrate formatting settings (see {@link #getCodeSample(LanguageCodeStyleSettingsProvider.SettingsType)}
   * @return a PSI file instance with given text, or null for default implementation using provider's language.
   */
  public @Nullable PsiFile createFileFromText(@NotNull Project project, @NotNull String text) {
    return null;
  }

  /**
   * Creates an instance of {@code CommonCodeStyleSettings} and sets initial default values for those
   * settings which differ from the original.
   *
   * @return Created instance of {@code CommonCodeStyleSettings} or null if associated language doesn't
   *         use its own language-specific common settings (the settings are shared with other languages).
   * @deprecated Override {@link #customizeDefaults(CommonCodeStyleSettings, IndentOptions)} method instead.
   */
  @Override
  @Deprecated
  public @NotNull CommonCodeStyleSettings getDefaultCommonSettings() {
    CommonCodeStyleSettings defaultSettings = new CommonCodeStyleSettings(getLanguage());
    defaultSettings.initIndentOptions();
    //noinspection ConstantConditions
    customizeDefaults(defaultSettings, defaultSettings.getIndentOptions());
    return defaultSettings;
  }

  /**
   * Customize default settings: set values which are different from the ones after {@code CommonCodeStyleSettings} initialization.
   *
   * @param commonSettings Customizable instance of  common settings for the language.
   * @param indentOptions  Customizable instance of indent options for the language.
   */
  protected void customizeDefaults(@NotNull CommonCodeStyleSettings commonSettings, @NotNull IndentOptions indentOptions) {
  }

  /**
   * @deprecated use {@link #getPriority()}
   */
  @Deprecated
  public DisplayPriority getDisplayPriority() {
    return getPriority();
  }

  /**
   * @return A list of languages from which code style settings can be copied to this provider's language settings. By default, all languages
   *         with code style settings are returned. In UI the languages are shown in the same order they are in the list.
   * @see #getLanguagesWithCodeStyleSettings()
   */
  public List<Language> getApplicableLanguages() {
    return getLanguagesWithCodeStyleSettings();
  }

  /**
   * @return A list of languages with code style settings, namely for which {@code LanguageCodeStyleSettingsProvider} exists.
   *         The list is ordered by language names as returned by {@link #getLanguageName(Language)} method.
   */
  public static @NotNull List<Language> getLanguagesWithCodeStyleSettings() {
    final ArrayList<Language> languages = new ArrayList<>();
    for (LanguageCodeStyleSettingsProvider provider : getAllProviders()) {
      if (provider.hasSettingsPage()) {
        languages.add(provider.getLanguage());
      }
    }
    languages.sort((l1, l2) -> Comparing.compare(getLanguageName(l1), getLanguageName(l2)));
    return languages;
  }

  public static @Nullable String getCodeSample(Language lang, @NotNull SettingsType settingsType) {
    final LanguageCodeStyleSettingsProvider provider = forLanguage(lang);
    return provider != null ? provider.getCodeSample(settingsType) : null;
  }

  public static int getRightMargin(Language lang, @NotNull SettingsType settingsType) {
    final LanguageCodeStyleSettingsProvider provider = forLanguage(lang);
    return provider != null ? provider.getRightMargin(settingsType) : -1;
  }

  @Override
  public abstract @NotNull Language getLanguage();

  public static @Nullable Language getLanguage(String langName) {
    for (LanguageCodeStyleSettingsProvider provider : getAllProviders()) {
      String name = provider.getLanguageName();
      if (name == null) name = provider.getLanguage().getDisplayName();
      if (langName.equals(name)) {
        return provider.getLanguage();
      }
    }
    return null;
  }

  public static @Nullable String getFileExt(Language lang) {
    final LanguageCodeStyleSettingsProvider provider = forLanguage(lang);
    return provider != null ? provider.getFileExt() : null;
  }

  /**
   * Returns a language name to be shown in UI. Used to overwrite language's display name by another name to
   * be shown in UI.
   *
   * @param lang The language whose display name must be return.
   * @return Alternative UI name defined by provider.getLanguageName() method or (if the method returns null)
   *         language's own display name.
   */
  public static @NotNull @NlsSafe String getLanguageName(Language lang) {
    final LanguageCodeStyleSettingsProvider provider = forLanguage(lang);
    String providerLangName = provider != null ? provider.getLanguageName() : null;
    return providerLangName != null ? providerLangName : lang.getDisplayName();
  }

  public static @Nullable LanguageCodeStyleSettingsProvider forLanguage(final Language language) {
    for (LanguageCodeStyleSettingsProvider provider : getAllProviders()) {
      if (provider.getLanguage().equals(language)) {
        return provider;
      }
    }
    return null;
  }

  /**
   * Searches a provider for a specific language or its base language.
   *
   * @param language The original language.
   * @return Found provider or {@code null} if it doesn't exist neither for the language itself nor for any of its base languages.
   */
  public static @Nullable LanguageCodeStyleSettingsProvider findUsingBaseLanguage(final @NotNull Language language) {
    for (Language currLang = language; currLang != null;  currLang = currLang.getBaseLanguage()) {
      LanguageCodeStyleSettingsProvider curr = forLanguage(currLang);
      if (curr != null) return curr;
    }
    return null;
  }

  public static @Nullable LanguageCodeStyleSettingsProvider findByExternalLanguageId(@NotNull String languageId) {
    return ContainerUtil.find(getAllProviders(), provider -> provider.getExternalLanguageId().equals(languageId));
  }

  public @Nullable IndentOptionsEditor getIndentOptionsEditor() {
    return null;
  }

  @Override
  public Set<String> getSupportedFields() {
    return new SupportedFieldCollector().collectFields();
  }

  public Set<String> getSupportedFields(SettingsType type) {
    return new SupportedFieldCollector().collectFields(type);
  }

  private final class SupportedFieldCollector implements CodeStyleSettingsCustomizable {
    private final Set<String> myCollectedFields = new HashSet<>();
    private SettingsType myCurrSettingsType;

    Set<String> collectFields() {
      for (SettingsType settingsType : SettingsType.values()) {
        myCurrSettingsType = settingsType;
        customizeSettings(this, settingsType);
      }
      return myCollectedFields;
    }

    Set<String> collectFields(SettingsType type) {
      myCurrSettingsType = type;
      customizeSettings(this, type);
      return myCollectedFields;
    }

    @Override
    public void showAllStandardOptions() {
      switch (myCurrSettingsType) {
        case BLANK_LINES_SETTINGS:
          for (BlankLinesOption blankLinesOption : BlankLinesOption.values()) {
            myCollectedFields.add(blankLinesOption.name());
          }
          break;
        case SPACING_SETTINGS:
          for (SpacingOption spacingOption : SpacingOption.values()) {
            myCollectedFields.add(spacingOption.name());
          }
          break;
        case WRAPPING_AND_BRACES_SETTINGS:
          for (WrappingOrBraceOption wrappingOrBraceOption : WrappingOrBraceOption.values()) {
            myCollectedFields.add(wrappingOrBraceOption.name());
          }
          break;
        case COMMENTER_SETTINGS:
          for (CommenterOption commenterOption : CommenterOption.values()) {
            myCollectedFields.add(commenterOption.name());
          }
          break;
        case INDENT_SETTINGS:
          for (IndentOption indentOption : IndentOption.values()) {
            myCollectedFields.add(indentOption.name());
          }
        default:
          // ignore
      }
    }

    @Override
    public void showStandardOptions(String... optionNames) {
      ContainerUtil.addAll(myCollectedFields, optionNames);
    }

    @Override
    public void showCustomOption(@NotNull Class<? extends CustomCodeStyleSettings> settingsClass,
                                 @NonNls @NotNull String fieldName,
                                 @NlsContexts.Label @NotNull String title,
                                 @Nls @Nullable String groupName,
                                 Object... options) {
      myCollectedFields.add(fieldName);
    }

    @Override
    public void showCustomOption(@NotNull Class<? extends CustomCodeStyleSettings> settingsClass,
                                 @NonNls @NotNull String fieldName,
                                 @NlsContexts.Label @NotNull String title,
                                 @Nls @Nullable String groupName,
                                 @Nullable OptionAnchor anchor,
                                 @NonNls @Nullable String anchorFieldName,
                                 Object... options) {
      myCollectedFields.add(fieldName);
    }
  }

  /**
   * Returns a wrapper around language's own code documentation comment settings from the given {@code rootSettings}.
   * @param rootSettings Root code style setting to retrieve doc comment settings from.
   * @return {@code DocCommentSettings} wrapper object which allows to retrieve and modify language's own
   *         settings related to doc comment. The object is used then by common platform doc comment handling algorithms.
   */
  @Override
  public @NotNull DocCommentSettings getDocCommentSettings(@NotNull CodeStyleSettings rootSettings) {
    return DocCommentSettings.DEFAULTS;
  }

  /**
   * Create a code style configurable for the given base settings and model settings.
   *
   * @param baseSettings  The base (initial) settings before changes.
   * @param modelSettings The settings to which UI changes are applied.
   * @return The code style configurable.
   *
   * @see CodeStyleConfigurable
   */
  @Override
  public @NotNull CodeStyleConfigurable createConfigurable(@NotNull CodeStyleSettings baseSettings, @NotNull CodeStyleSettings modelSettings) {
    throw new RuntimeException(
      this.getClass().getCanonicalName() + " for language #" + getLanguage().getID() + " doesn't implement createConfigurable()");
  }

  private static final AtomicReference<Set<LanguageCodeStyleSettingsProvider>> ourSettingsPagesProviders = new AtomicReference<>();

  /**
   * @return A list of providers implementing {@link #createConfigurable(CodeStyleSettings, CodeStyleSettings)}
   */
  public static Set<LanguageCodeStyleSettingsProvider> getSettingsPagesProviders() {
    return ourSettingsPagesProviders.updateAndGet(providers -> providers != null ? providers : calcSettingPagesProviders());
  }

  public static void cleanSettingsPagesProvidersCache() {
    ourSettingsPagesProviders.set(null);
  }

  private static @NotNull Set<LanguageCodeStyleSettingsProvider> calcSettingPagesProviders() {
    Set<LanguageCodeStyleSettingsProvider> settingsPagesProviders = new HashSet<>();
    for (LanguageCodeStyleSettingsProvider provider : getAllProviders()) {
      registerSettingsPageProvider(settingsPagesProviders, provider);
    }
    return settingsPagesProviders;
  }

  @ApiStatus.Internal
  public static void registerSettingsPageProvider(@NotNull LanguageCodeStyleSettingsProvider provider) {
    registerSettingsPageProvider(getSettingsPagesProviders(), provider);
  }

  @ApiStatus.Internal
  public static void unregisterSettingsPageProvider(@NotNull LanguageCodeStyleSettingsProvider provider) {
    getSettingsPagesProviders().remove(provider);
  }

  private static void registerSettingsPageProvider(@NotNull Set<? super LanguageCodeStyleSettingsProvider> settingsPagesProviders,
                                                   @NotNull LanguageCodeStyleSettingsProvider provider) {
    try {
      Method
        configMethod = provider.getClass().getMethod("createConfigurable", CodeStyleSettings.class, CodeStyleSettings.class);
      Class<?> declaringClass = configMethod.getDeclaringClass();
      if (!declaringClass.equals(LanguageCodeStyleSettingsProvider.class)) {
        settingsPagesProviders.add(provider);
      }
    }
    catch (NoSuchMethodException e) {
      // Do not add the provider.
    }
  }

  @ApiStatus.Experimental
  public final @NotNull AbstractCodeStylePropertyMapper getPropertyMapper(@NotNull CodeStyleSettings settings) {
    return new LanguageCodeStylePropertyMapper(settings, getLanguage(), getExternalLanguageId());
  }

  @SuppressWarnings("rawtypes")
  @ApiStatus.Experimental
  public @Nullable CodeStyleFieldAccessor getAccessor(@NotNull Object codeStyleObject, @NotNull Field field) {
    return null;
  }

  @SuppressWarnings("rawtypes")
  public List<CodeStylePropertyAccessor> getAdditionalAccessors(@NotNull Object codeStyleObject) {
    return Collections.emptyList();
  }

  /**
   * Tells is the provider supports external formats such as Json and .editorconfig. By default, it is assumed that language
   * code style settings use a standard way to define settings, namely via public fields which have a straightforward mapping
   * between the fields and human-readable stored values without extra transformation. If not, the provider must implement
   * {@code getAccessor()} method for fields using magic constants, specially encoded strings etc., and
   * {@code getAdditionalAccessors()} method for non-standard properties using their own {@code writer/readExternal() methods}
   * for serialization.
   * @return True (default) if standard properties are supported, false to disable language settings to avoid export
   * partial, non-readable etc. data till proper accessors are implemented.
   */
  public boolean supportsExternalFormats() {
    return true;
  }
}
