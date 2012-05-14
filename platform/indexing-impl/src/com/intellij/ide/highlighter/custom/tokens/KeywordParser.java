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

package com.intellij.ide.highlighter.custom.tokens;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.tree.IElementType;
import gnu.trove.THashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author dsl
 */
public class KeywordParser extends TokenParser {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.highlighter.custom.tokens.KeywordParser");
  private final List<Set<String>> myKeywordSets = new ArrayList<Set<String>>();
  private final Pattern myPattern;
  private final boolean myIgnoreCase;

  public KeywordParser(List<Set<String>> keywordSets, boolean ignoreCase) {
    myIgnoreCase = ignoreCase;
    LOG.assertTrue(keywordSets.size() == CustomHighlighterTokenType.KEYWORD_TYPE_COUNT);
    StringBuilder regex = new StringBuilder();
    for (Set<String> keywordSet : keywordSets) {
      myKeywordSets.add(getKeywordSet(keywordSet));
      for (String word : keywordSet) {
        if (regex.length() > 0) {
          regex.append("|");
        }
        regex.append(escapeSpecialCharacters(word));
      }
    }
    Pattern pattern = null;
    try {
      String pat = "(" + regex + ")($|[^\\w-])";
      pattern = Pattern.compile(pat, (ignoreCase ? Pattern.CASE_INSENSITIVE : 0) | Pattern.DOTALL);
    }
    catch (PatternSyntaxException e) {
      LOG.info(e);
    }
    myPattern = pattern;
  }

  private static String escapeSpecialCharacters(String word) {
    StringBuilder esc = new StringBuilder();
    word = word.replace("\\", "\\\\");
    for (int i = 0; i < word.length(); i++) {
      char ch = word.charAt(i);
      if ("-*+?$%^.(){}[]|".indexOf(ch) >= 0) esc.append('\\');
      esc.append(ch);
    }
    word = esc.toString();
    return word;
  }

  private Set<String> getKeywordSet(Set<String> keywordSet) {
    if (!myIgnoreCase) {
      return new THashSet<String>(keywordSet);
    }

    final Set<String> result = new THashSet<String>();
    for (String s : keywordSet) {
      result.add(StringUtilRt.toUpperCase(s));
    }
    return result;
  }

  public boolean hasToken(int position) {
    if (myPattern == null) {
      return false;
    }

    Matcher matcher = myPattern.matcher(myBuffer.subSequence(position, myBuffer.length()));
    if (!matcher.lookingAt()) {
      return false;
    }

    String keyword = matcher.group(1);
    String testKeyword = myIgnoreCase ? StringUtilRt.toUpperCase(keyword) : keyword;
    for (int i = 0; i < CustomHighlighterTokenType.KEYWORD_TYPE_COUNT; i++) {
      if (myKeywordSets.get(i).contains(testKeyword)) {
        myTokenInfo.updateData(position, position + keyword.length(), getToken(i));
        return true;
      }
    }

    return false;
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
