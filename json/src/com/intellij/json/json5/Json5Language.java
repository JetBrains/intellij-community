// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.json5;

import com.intellij.json.JsonLanguage;

public class Json5Language extends JsonLanguage {
  public static final Json5Language INSTANCE = new Json5Language();

  protected Json5Language() {
    super("JSON5", "application/json5");
  }

  @Override
  public boolean hasPermissiveStrings() {
    return true;
  }
}
