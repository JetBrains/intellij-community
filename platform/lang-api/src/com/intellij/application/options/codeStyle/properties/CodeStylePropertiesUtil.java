// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;

public final class CodeStylePropertiesUtil {

  private CodeStylePropertiesUtil() {}

  public static void collectMappers(@NotNull CodeStyleSettings settings,
                                    @NotNull Consumer<? super AbstractCodeStylePropertyMapper> collector) {
    for (LanguageCodeStyleSettingsProvider provider : LanguageCodeStyleSettingsProvider.EP_NAME.getExtensionList()) {
      if (provider.supportsExternalFormats()) {
        collector.accept(provider.getPropertyMapper(settings));
      }
    }
    collector.accept(new GeneralCodeStylePropertyMapper(settings));
  }

  @NotNull
  public static List<String> getValueList(@NotNull String string) {
    return ContainerUtil.map(string.split(","), s -> s.trim());
  }

  public static <T> String toCommaSeparatedString(@NotNull List<T> list) {
    StringBuilder builder = new StringBuilder();
    for (T value : list) {
      if (builder.length() > 0) {
        builder.append(",");
      }
      builder.append(value);
    }
    return builder.toString();
  }
}
