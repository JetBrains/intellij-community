/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.highlighting;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.StdTokenSets;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public class JavaPairedBraceMatcher extends PairedBraceAndAnglesMatcher {
  private static class Holder {
    private static final TokenSet TYPE_TOKENS =
      TokenSet.orSet(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET,
                     TokenSet.create(JavaTokenType.IDENTIFIER, JavaTokenType.COMMA,
                                     JavaTokenType.AT,//anno
                                     JavaTokenType.RBRACKET, JavaTokenType.LBRACKET, //arrays
                                     JavaTokenType.QUEST, JavaTokenType.EXTENDS_KEYWORD, JavaTokenType.SUPER_KEYWORD));//wildcards
  }

  public JavaPairedBraceMatcher() {
    super(new JavaBraceMatcher(), JavaLanguage.INSTANCE, JavaFileType.INSTANCE, Holder.TYPE_TOKENS);
  }

  @Override
  public @NotNull IElementType lt() {
    return JavaTokenType.LT;
  }

  @Override
  public @NotNull IElementType gt() {
    return JavaTokenType.GT;
  }
}
