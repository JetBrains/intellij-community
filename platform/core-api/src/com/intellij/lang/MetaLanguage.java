// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Allows to register a language extension for a group of languages defined by a certain criterion.
 * To use this, specify the ID of a meta-language in the "{@code language}" attribute of an extension in {@code plugin.xml}.
 */
public abstract class MetaLanguage extends Language {
  public static final ExtensionPointName<MetaLanguage> EP_NAME = new ExtensionPointName<>("com.intellij.metaLanguage");

  protected MetaLanguage(@NotNull @NonNls String ID) {
    super(ID);
    EP_NAME.addExtensionPointListener(new ExtensionPointListener<MetaLanguage>() {
      @Override
      public void extensionRemoved(@NotNull MetaLanguage metaLanguage, @NotNull PluginDescriptor pluginDescriptor) {
        if (MetaLanguage.this == metaLanguage) {
          for (Language matchingLanguage : metaLanguage.getMatchingLanguages()) {
            LanguageUtil.clearMatchingMetaLanguagesCache(matchingLanguage);
          }
          metaLanguage.unregisterLanguage(pluginDescriptor);
        }
      }
    }, null);
  }

  public static @NotNull List<MetaLanguage> all() {
    return EP_NAME.getExtensionList();
  }

  /**
   * Checks if the given language matches the criterion of this meta-language.
   */
  public abstract boolean matchesLanguage(@NotNull Language language);

  /**
   * Returns the list of all languages matching this meta-language.
   */
  public @NotNull Collection<Language> getMatchingLanguages() {
    return ContainerUtil.filter(Language.getRegisteredLanguages(), language -> matchesLanguage(language));
  }

  @ApiStatus.Internal
  public static void clearAllMatchingMetaLanguagesCache() {
    for (Language language : Language.getRegisteredLanguages()) {
      LanguageUtil.clearMatchingMetaLanguagesCache(language);
    }
  }
}
