// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.highlighter.custom.tokens;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.CharTrie;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author dsl
 * @author peter
 */
public final class KeywordParser {
  private static final Logger LOG = Logger.getInstance(KeywordParser.class);
  private final List<Set<String>> myKeywordSets = new ArrayList<>();
  private final CharTrie myTrie = new CharTrie();
  private final IntSet myHashCodes = new IntOpenHashSet();
  private final boolean myIgnoreCase;

  public KeywordParser(List<Set<String>> keywordSets, boolean ignoreCase) {
    myIgnoreCase = ignoreCase;
    LOG.assertTrue(keywordSets.size() == CustomHighlighterTokenType.KEYWORD_TYPE_COUNT);
    for (Set<String> keywordSet : keywordSets) {
      Set<String> normalized = normalizeKeywordSet(keywordSet);
      myKeywordSets.add(normalized);
      for (String s : normalized) {
        myHashCodes.add(myTrie.getHashCode(s));
      }
    }
  }

  private Set<String> normalizeKeywordSet(Set<String> keywordSet) {
    if (!myIgnoreCase) {
      return new HashSet<>(keywordSet);
    }

    final Set<String> result = new HashSet<>();
    for (String s : keywordSet) {
      result.add(StringUtil.toUpperCase(s));
    }
    return result;
  }

  public boolean hasToken(int position, CharSequence myBuffer, @Nullable TokenInfo tokenInfo) {
    int index = 0;
    int offset = position;
    String longestKeyword = null;
    IElementType longestKeywordType = null;
    while (offset < myBuffer.length()) {
      char c = myBuffer.charAt(offset++);
      int nextIndex = myTrie.findSubNode(index, myIgnoreCase ? Character.toUpperCase(c) : c);
      if (nextIndex == 0) {
        break;
      }
      index = nextIndex;
      if (myHashCodes.contains(index) && isWordEnd(offset, myBuffer)) {
        String keyword = myBuffer.subSequence(position, offset).toString();
        String testKeyword = myIgnoreCase ? StringUtil.toUpperCase(keyword) : keyword;
        for (int i = 0; i < CustomHighlighterTokenType.KEYWORD_TYPE_COUNT; i++) {
          if (myKeywordSets.get(i).contains(testKeyword)) {
            longestKeyword = testKeyword;
            longestKeywordType = getToken(i);
            break;
          }
        }
      }
    }

    if (longestKeyword != null && tokenInfo != null) {
      tokenInfo.updateData(position, position + longestKeyword.length(), longestKeywordType);
    }

    return longestKeyword != null;
  }

  private static boolean isWordEnd(int offset, CharSequence sequence) {
    if (offset == sequence.length()) {
      return true;
    }

    return !Character.isJavaIdentifierPart(sequence.charAt(offset - 1)) || !Character.isJavaIdentifierPart(sequence.charAt(offset));
  }

  private static IElementType getToken(int keywordSetIndex) {
    switch(keywordSetIndex) {
      case 0: return CustomHighlighterTokenType.KEYWORD_1;
      case 1: return CustomHighlighterTokenType.KEYWORD_2;
      case 2: return CustomHighlighterTokenType.KEYWORD_3;
      case 3: return CustomHighlighterTokenType.KEYWORD_4;
    }
    throw new AssertionError(keywordSetIndex);
  }
}
