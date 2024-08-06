// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.json5;

import com.intellij.lexer.FlexAdapter;

public final class Json5Lexer extends FlexAdapter {
  public Json5Lexer() {
    super(new _Json5Lexer());
  }
}
