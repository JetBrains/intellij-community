// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.enter;

public class EnterBetweenBracesAndBracketsDelegate extends EnterBetweenBracesDelegate {
  @Override
  protected boolean isBracePair(char lBrace, char rBrace) {
    return super.isBracePair(lBrace, rBrace ) || (lBrace == '[' && rBrace == ']');
  }
}
