// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath;

import com.intellij.icons.AllIcons;
import com.intellij.json.JsonBundle;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class JsonPathFileType extends LanguageFileType {

  public static final JsonPathFileType INSTANCE = new JsonPathFileType();

  private JsonPathFileType() {
    super(JsonPathLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public String getName() {
    return "JSONPath";
  }

  @NotNull
  @Override
  public String getDescription() {
    return JsonBundle.message("filetype.jsonpath.description");
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return "jsonpath";
  }

  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Json;
  }
}
