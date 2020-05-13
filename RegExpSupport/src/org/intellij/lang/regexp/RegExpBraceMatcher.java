/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.intellij.lang.regexp;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RegExpBraceMatcher implements PairedBraceMatcher {
  @Override
  public BracePair @NotNull [] getPairs() {
    return new BracePair[]{
      new BracePair(RegExpTT.GROUP_BEGIN, RegExpTT.GROUP_END, true),
      new BracePair(RegExpTT.SET_OPTIONS, RegExpTT.GROUP_END, true), new BracePair(RegExpTT.NON_CAPT_GROUP, RegExpTT.GROUP_END, true),
      new BracePair(RegExpTT.ATOMIC_GROUP, RegExpTT.GROUP_END, true),
      new BracePair(RegExpTT.POS_LOOKAHEAD, RegExpTT.GROUP_END, true), new BracePair(RegExpTT.NEG_LOOKAHEAD, RegExpTT.GROUP_END, true),
      new BracePair(RegExpTT.POS_LOOKBEHIND, RegExpTT.GROUP_END, true), new BracePair(RegExpTT.NEG_LOOKBEHIND, RegExpTT.GROUP_END, true),
      new BracePair(RegExpTT.PYTHON_NAMED_GROUP, RegExpTT.GROUP_END, true),
      new BracePair(RegExpTT.PYTHON_NAMED_GROUP_REF, RegExpTT.GROUP_END, true),
      new BracePair(RegExpTT.PCRE_RECURSIVE_NAMED_GROUP_REF, RegExpTT.GROUP_END, true),
      new BracePair(RegExpTT.PCRE_COND_REF, RegExpTT.GROUP_END, true),
      new BracePair(RegExpTT.PCRE_BRANCH_RESET, RegExpTT.GROUP_END, true),
      new BracePair(RegExpTT.RUBY_NAMED_GROUP, RegExpTT.GROUP_END, true),
      new BracePair(RegExpTT.RUBY_QUOTED_NAMED_GROUP, RegExpTT.GROUP_END, true),
      new BracePair(RegExpTT.RUBY_NAMED_GROUP_REF, RegExpTT.GT, true),
      new BracePair(RegExpTT.RUBY_QUOTED_NAMED_GROUP_REF, RegExpTT.QUOTE, true),
      new BracePair(RegExpTT.RUBY_NAMED_GROUP_CALL, RegExpTT.GT, true),
      new BracePair(RegExpTT.RUBY_QUOTED_NAMED_GROUP_CALL, RegExpTT.QUOTE, true),
      new BracePair(RegExpTT.CLASS_BEGIN, RegExpTT.CLASS_END, false), new BracePair(RegExpTT.LBRACE, RegExpTT.RBRACE, false),
      new BracePair(RegExpTT.QUOTE_BEGIN, RegExpTT.QUOTE_END, false),
      new BracePair(RegExpTT.BRACKET_EXPRESSION_BEGIN, RegExpTT.BRACKET_EXPRESSION_END, false)};
  }

  @Override
  public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType) {
    return false;
  }

  @Override
  public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
    return openingBraceOffset;
  }
}
