/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.psi.impl.search;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 *
 * @see com.intellij.psi.impl.cache.impl.todo.LexerBasedTodoIndexer
 */
public interface IndexPatternBuilder {
  ExtensionPointName<IndexPatternBuilder> EP_NAME = ExtensionPointName.create("com.intellij.indexPatternBuilder");

  @Nullable
  Lexer getIndexingLexer(@NotNull PsiFile file);
  @Nullable
  TokenSet getCommentTokenSet(@NotNull PsiFile file);
  int getCommentStartDelta(IElementType tokenType);
  int getCommentEndDelta(IElementType tokenType);
}
