// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Mikhail Golubev
 */
public class JsonFileType extends LanguageFileType {
  public static final JsonFileType INSTANCE = new JsonFileType();
  public static final String DEFAULT_EXTENSION = "json";

  protected JsonFileType(Language language) {
    super(language);
  }

  protected JsonFileType(Language language, boolean secondary) {
    super(language, secondary);
  }

  protected JsonFileType() {
    super(JsonLanguage.INSTANCE);
  }

  @Override
  public @NotNull String getName() {
    return "JSON";
  }

  @Override
  public @NotNull String getDescription() {
    return JsonBundle.message("filetype.json.description");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    // TODO: add JSON icon instead
    return AllIcons.FileTypes.Json;
  }
}
