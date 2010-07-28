/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.java.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import org.jetbrains.annotations.Nullable;


public class StatementParser {
  private StatementParser() { }

  @Nullable
  public static PsiBuilder.Marker parseCodeBlock(final PsiBuilder builder) {
    if (builder.getTokenType() != JavaTokenType.LBRACE) return null;

    final PsiBuilder.Marker codeBlock = builder.mark();
    builder.advanceLexer();

    // temp
    if (builder.getTokenType() == JavaTokenType.RBRACE) {
      builder.advanceLexer();
      codeBlock.done(JavaElementType.CODE_BLOCK);
      return codeBlock;
    }

    // todo: implement
    throw new UnsupportedOperationException(builder.toString());
  }
}
