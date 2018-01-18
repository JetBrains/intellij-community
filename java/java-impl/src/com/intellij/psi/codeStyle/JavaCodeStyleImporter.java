/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.psi.codeStyle;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.externalSystem.service.project.settings.CodeStyleConfigurationImporter;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class JavaCodeStyleImporter implements CodeStyleConfigurationImporter<JavaCodeStyleSettings> {
  @Override
  public void processSettings(@NotNull JavaCodeStyleSettings settings, @NotNull Map config) {
    asInt(config.get("CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND"), (it) -> settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = it);
    asBoolean(config.get("JD_ALIGN_PARAM_COMMENTS"), (it) -> settings.JD_ALIGN_PARAM_COMMENTS = it);
    asBoolean(config.get("JD_ALIGN_EXCEPTION_COMMENTS"), (it) -> settings.JD_ALIGN_EXCEPTION_COMMENTS = it);
    asBoolean(config.get("JD_P_AT_EMPTY_LINES"), (it) -> settings.JD_P_AT_EMPTY_LINES = it);
    asBoolean(config.get("JD_KEEP_EMPTY_PARAMETER"), (it) -> settings.JD_KEEP_EMPTY_PARAMETER = it);
    asBoolean(config.get("JD_KEEP_EMPTY_EXCEPTION"), (it) -> settings.JD_KEEP_EMPTY_EXCEPTION = it);
    asBoolean(config.get("JD_KEEP_EMPTY_RETURN"), (it) -> settings.JD_KEEP_EMPTY_RETURN = it);
  }

  @Override
  public boolean canImport(@NotNull String langName) {
    return "java".equals(langName);
  }

  @NotNull
  @Override
  public Class<JavaCodeStyleSettings> getCustomClass() {
    return JavaCodeStyleSettings.class;
  }

  @NotNull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  private void asInt(Object value, Consumer<Integer> consumer) {
    if (value instanceof Number) {
      consumer.consume(((Number)value).intValue());
    }
  }

  private void asBoolean(Object value, Consumer<Boolean> consumer) {
    if (value instanceof Boolean) {
      consumer.consume((Boolean)value);
    }
  }
}
