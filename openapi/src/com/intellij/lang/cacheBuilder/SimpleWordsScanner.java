package com.intellij.lang.cacheBuilder;

import com.intellij.util.Processor;

/**
 * @author max
 */
public class SimpleWordsScanner implements WordsScanner {
  public void processWords(CharSequence fileText, Processor<WordOccurence> processor) {
    int index = 0;
    ScanWordsLoop:
    while (true) {
      while (true) {
        if (index == fileText.length()) break ScanWordsLoop;
        char c = fileText.charAt(index);
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
        char c = fileText.charAt(index);
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) continue;
        if (!Character.isJavaIdentifierPart(c) || c == '$') break;
      }
      if (index - index1 > 100) continue; // Strange limit but we should have some!

      final CharSequence text = fileText.subSequence(index1, index);
      processor.process(new WordOccurence(text, null));
    }
  }
}
