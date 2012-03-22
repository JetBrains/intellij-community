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

/**
 * Implemented by a custom language plugin to define how texts in the language are
 * broken into words. Used to build a word index which is later used for Find Usages.
 *
 * @author max
 * @see com.intellij.lang.findUsages.FindUsagesProvider#getWordsScanner()
 * @see SimpleWordsScanner
 * @see DefaultWordsScanner
 */
public interface WordsScanner {
  /**
   * Processes the specified text fragment and passes every word in the text to the
   * specified processor.
   *
   * @param fileText  the text to break into words.
   * @param processor the processor which accepts the words in the text.
   */
  void processWords(CharSequence fileText, Processor<WordOccurrence> processor);
}
