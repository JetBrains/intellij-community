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

  /** returns id in char table for part of char buffer */
  int getId(char[] buffer, int offset, int length);
  /** returns id in char table for String */
  int getId(String str);

  /** return token entry by id */
  Entry getEntry(int id);
  /** copies to buffer contents of token text by id
   @return offset in buffer
   */
  int copyTo(int id, char[] buffer, int startOffset); 

  int checkId(String str);

  interface Entry{
    char[] getBuffer();
    int getOffset();
    int getLength();
  }
}
