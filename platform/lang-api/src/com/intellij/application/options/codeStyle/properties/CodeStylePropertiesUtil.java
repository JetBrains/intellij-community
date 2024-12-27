// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.function.Consumer;

public final class CodeStylePropertiesUtil {

  private CodeStylePropertiesUtil() {}

  public static void collectMappers(@NotNull CodeStyleSettings settings,
                                    @NotNull Consumer<? super AbstractCodeStylePropertyMapper> collector) {
    for (LanguageCodeStyleSettingsProvider provider : LanguageCodeStyleSettingsProvider.getAllProviders()) {
      if (provider.supportsExternalFormats()) {
        collector.accept(provider.getPropertyMapper(settings));
      }
    }
    collector.accept(new GeneralCodeStylePropertyMapper(settings));
  }

  public static @Unmodifiable @NotNull List<String> getValueList(@NotNull String string) {
    return ContainerUtil.map(string.split(","), s -> s.trim());
  }

  public static <T> String toCommaSeparatedString(@NotNull List<T> list) {
    StringBuilder builder = new StringBuilder();
    for (T value : list) {
      if (!builder.isEmpty()) {
        builder.append(",");
      }
      builder.append(value);
    }
    return builder.toString();
  }

  public static boolean isAccessorAllowingEmptyList(@NotNull CodeStylePropertyAccessor<?> accessor) {
    return accessor instanceof CodeStyleValueList && ((CodeStyleValueList)accessor).isEmptyListAllowed();
  }
}
