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
import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.tree.IElementType;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * @author dsl
 */
public class KeywordParser extends TokenParser {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.highlighter.custom.tokens.KeywordParser");
  private final HashSet[] myKeywordSets = new HashSet[CustomHighlighterTokenType.KEYWORD_TYPE_COUNT];
  private final boolean myIgnoreCase;
  private final BitSet myFirstCharacters = new BitSet();
  private final BitSet myCharacters = new BitSet();

  public KeywordParser(Set[] keywordSets, boolean ignoreCase) {
    LOG.assertTrue(keywordSets.length == myKeywordSets.length);
    myIgnoreCase = ignoreCase;
    int maxLength = 0;
    for (int i = 0; i < keywordSets.length; i++) {
      Set keywordSet = keywordSets[i];
      myKeywordSets[i] = getKeywordSet(keywordSet);
      for (Iterator iterator = keywordSet.iterator(); iterator.hasNext();) {
        String s = (String) iterator.next();
        maxLength = Math.max(maxLength, s.length());

        final char firstChar = s.charAt(0);
        if (ignoreCase) {
          myFirstCharacters.set(Character.toUpperCase(firstChar), Character.toUpperCase(firstChar) + 1);
          myFirstCharacters.set(Character.toLowerCase(firstChar), Character.toLowerCase(firstChar) + 1);
        } else {
          myFirstCharacters.set(firstChar, firstChar + 1);
        }
        for (int j = 0; j < s.length(); j++) {
          final char currentChar = s.charAt(j);
          if (ignoreCase) {
            myCharacters.set(Character.toUpperCase(currentChar), Character.toUpperCase(currentChar) + 1);
            myCharacters.set(Character.toLowerCase(currentChar), Character.toLowerCase(currentChar) + 1);
          }
          else {
            myCharacters.set(currentChar, currentChar + 1);
          }
        }
      }
    }
  }

  private HashSet getKeywordSet(Set keywordSet) {
    if (!myIgnoreCase) {
      return new HashSet(keywordSet);
    } else {
      final HashSet result = new HashSet();
      for (Iterator iterator = keywordSet.iterator(); iterator.hasNext();) {
        String s = (String) iterator.next();
        result.add(s.toUpperCase());
      }
      return result;
    }
  }


  public boolean hasToken(int position) {
    if (!myFirstCharacters.get(myBuffer.charAt(position))) return false;
    final int start = position;
    for (position++; position < myEndOffset; position++) {
      final char c = myBuffer.charAt(position);
      if (!myCharacters.get(c)) {
        if (Character.isJavaIdentifierPart(c)) return false;
        break;
      }
    }

    final String keyword = myBuffer.subSequence(start, position).toString();
    String testKeyword = myIgnoreCase ? keyword.toUpperCase() : keyword;
    for (int i = 0; i < myKeywordSets.length; i++) {
      HashSet keywordSet = myKeywordSets[i];
      if (keywordSet.contains(testKeyword)) {
        myTokenInfo.updateData(start, position, getToken(i));
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
    return null;
  }
}
