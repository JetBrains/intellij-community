// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.json5;

import com.intellij.json.JsonLanguage;

public final class Json5Language extends JsonLanguage {
  public static final Json5Language INSTANCE = new Json5Language();

  private Json5Language() {
    super("JSON5", "application/json5");
  }

  @Override
  public boolean hasPermissiveStrings() {
    return true;
  }
}
