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

import static com.intellij.util.ObjectUtils.consumeIfCast;

public class JavaCodeStyleImporter implements CodeStyleConfigurationImporter<JavaCodeStyleSettings> {
  @Override
  public void processSettings(@NotNull JavaCodeStyleSettings settings, @NotNull Map config) {
    consumeIfCast(config.get("CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND"), Number.class,
                  (it) -> settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = it.intValue());

    consumeIfCast(config.get("JD_ALIGN_PARAM_COMMENTS"), Boolean.class,
                  (it) -> settings.JD_ALIGN_PARAM_COMMENTS = it);

    consumeIfCast(config.get("JD_ALIGN_EXCEPTION_COMMENTS"), Boolean.class,
                  (it) -> settings.JD_ALIGN_EXCEPTION_COMMENTS = it);

    consumeIfCast(config.get("JD_P_AT_EMPTY_LINES"), Boolean.class,
                  (it) -> settings.JD_P_AT_EMPTY_LINES = it);

    consumeIfCast(config.get("JD_KEEP_EMPTY_PARAMETER"), Boolean.class,
                  (it) -> settings.JD_KEEP_EMPTY_PARAMETER = it);

    consumeIfCast(config.get("JD_KEEP_EMPTY_EXCEPTION"), Boolean.class,
                  (it) -> settings.JD_KEEP_EMPTY_EXCEPTION = it);

    consumeIfCast(config.get("JD_KEEP_EMPTY_RETURN"), Boolean.class,
                  (it) -> settings.JD_KEEP_EMPTY_RETURN = it);
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
}
