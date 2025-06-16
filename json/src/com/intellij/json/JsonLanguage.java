// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import com.intellij.lang.Language;

public class JsonLanguage extends Language {
  public static final JsonLanguage INSTANCE = new JsonLanguage();

  protected JsonLanguage(String ID, String... mimeTypes) {
    super(INSTANCE, ID, mimeTypes);
  }

  private JsonLanguage() {
    super("JSON", "application/json", "application/vnd.api+json", "application/hal+json", "application/ld+json");
  }

  @Override
  public boolean isCaseSensitive() {
    return true;
  }

  public boolean hasPermissiveStrings() { return false; }
}
