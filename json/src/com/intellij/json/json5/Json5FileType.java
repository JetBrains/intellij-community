// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.json5;

import com.intellij.json.JsonBundle;
import com.intellij.json.JsonFileType;
import org.jetbrains.annotations.NotNull;

public final class Json5FileType extends JsonFileType {
  public static final Json5FileType INSTANCE = new Json5FileType();
  public static final String DEFAULT_EXTENSION = "json5";

  private Json5FileType() {
    super(Json5Language.INSTANCE);
  }

  @Override
  public @NotNull String getName() {
    return "JSON5";
  }

  @Override
  public @NotNull String getDescription() {
    return JsonBundle.message("filetype.json5.description");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }
}
