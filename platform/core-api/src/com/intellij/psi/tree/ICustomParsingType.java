/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.tree;

import com.intellij.lang.ASTNode;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

/**
 * An additional interface to be implemented by {@link IElementType} instances for tokens which are more convenient to parse separately.
 * Parsing is done when leaf elements are created.<p/>
 *
 * Use this for cases similar to {@link ILazyParseableElementType}, but when its default implementation isn't sufficient.
 * For example, default lazy-parseable elements can't be stub-based (see {@link com.intellij.psi.stubs.IStubElementType}),
 * while {@link ICustomParsingType} gives you flexibility to achieve that.
 */
public interface ICustomParsingType {

  /**
   * Invoked by {@link com.intellij.lang.PsiBuilder} when it finds a token of this type,
   * instead of creating the leaf element for it in a default way.
   * @param text token text
   * @param table {@link CharTable} object used for interning string in the file
   * @return a tree element of this type with a given text.
   */
  @NotNull
  ASTNode parse(@NotNull CharSequence text, @NotNull CharTable table);
}
