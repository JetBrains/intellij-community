package com.intellij.util;

import com.intellij.openapi.util.Key;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 07.06.2004
 * Time: 15:28:03
 * To change this template use File | Settings | File Templates.
 */
public interface CharTable{
  int PAGE_SIZE = 256;
  Key<CharTable> CHAR_TABLE_KEY = new Key<CharTable>("Char table");

  CharSequence intern(final CharSequence text);
}
