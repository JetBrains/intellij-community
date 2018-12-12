// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

public class CodeStylePropertiesUtil {

  public static final String COMMON_OPTIONS_NAME = "All";

  private CodeStylePropertiesUtil() {}

  public static void collectMappers(@NotNull CodeStyleSettings settings,
                                    @NotNull BiConsumer<String, AbstractCodeStylePropertyMapper> collector) {
    for (LanguageCodeStyleSettingsProvider provider : LanguageCodeStyleSettingsProvider.EP_NAME.getExtensionList()) {
      final String languageName = provider.getLanguage().getDisplayName();
      collector.accept(ObjectUtils.notNull(provider.getLanguageName(), languageName),provider.getPropertyMapper(settings));
    }
    collector.accept(COMMON_OPTIONS_NAME, new GeneralCodeStylePropertyMapper(settings));
  }

}
