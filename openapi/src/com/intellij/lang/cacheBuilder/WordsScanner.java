package com.intellij.lang.cacheBuilder;

import com.intellij.util.Processor;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 31, 2005
 * Time: 9:07:44 PM
 * To change this template use File | Settings | File Templates.
 */
public interface WordsScanner {
  void processWords(CharSequence fileText, Processor<WordOccurence> processor);
}
