/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.lang.cacheBuilder;

import com.intellij.util.Processor;
import com.intellij.util.text.CharArrayUtil;

/**
 * The default primitive implementation of a words scanner. The implementation does not
 * use any lexer, breaks text into words at boundaries of sequences of English letters
 * and treats all words as occurred in unknown context.
 *
 * @author max
 */
public class SimpleWordsScanner implements WordsScanner {
  public void processWords(CharSequence fileText, Processor<WordOccurrence> processor) {
    int index = 0;
    WordOccurrence occurrence = null;
    final char[] fileTextArray = CharArrayUtil.fromSequenceWithoutCopying(fileText);

    ScanWordsLoop:
    while (true) {
      while (true) {
        if (index == fileText.length()) break ScanWordsLoop;
        final char c = fileTextArray != null ? fileTextArray[index]:fileText.charAt(index);
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') ||
            (Character.isJavaIdentifierStart(c) && c != '$')) {
          break;
        }
        index++;
      }
      int index1 = index;
      while (true) {
        index++;
        if (index == fileText.length()) break;
        final char c = fileTextArray != null ? fileTextArray[index]:fileText.charAt(index);
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) continue;
        if (!Character.isJavaIdentifierPart(c) || c == '$') break;
      }
      if (index - index1 > 100) continue; // Strange limit but we should have some!

      if (occurrence == null) occurrence = new WordOccurrence(fileText, index1, index, null);
      else occurrence.init(fileText, index1, index, null);
      processor.process(occurrence);
    }
  }
}
