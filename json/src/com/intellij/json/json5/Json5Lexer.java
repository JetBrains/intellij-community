// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.json5;

import com.intellij.lexer.FlexAdapter;

public class Json5Lexer extends FlexAdapter {
  public Json5Lexer() {
    super(new _Json5Lexer());
  }
}
