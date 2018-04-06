// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.json5;

import com.intellij.json.JsonFileType;
import org.jetbrains.annotations.NotNull;

public class Json5FileType extends JsonFileType {
  public static final Json5FileType INSTANCE = new Json5FileType();
  public static final String DEFAULT_EXTENSION = "json5";

  public Json5FileType() {
    super(Json5Language.INSTANCE);
  }

  @NotNull
  @Override
  public String getName() {
    return "JSON5";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "JSON5";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }
}
