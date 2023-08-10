// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;

public @NonNls interface FlexLexer {
  void yybegin(int state);
  int yystate();
  int getTokenStart();
  int getTokenEnd();
  IElementType advance() throws IOException;
  void reset(CharSequence buf, int start, int end, int initialState);
}
