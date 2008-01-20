package com.intellij.psi.impl.cache.index;

import com.intellij.openapi.util.text.StringUtil;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 16, 2008
 */
public final class IdIndexEntry {
  private final int myWordHashCode;
  private final int myOccurrenceMask;

  public IdIndexEntry(String word, int occurrenceMask, boolean caseSensitive) {
    this(caseSensitive? StringUtil.stringHashCode(word) : StringUtil.stringHashCodeInsensitive(word), occurrenceMask);
  }

  public IdIndexEntry(int wordHash, int occurrenceMask) {
    myWordHashCode = wordHash;
    myOccurrenceMask = occurrenceMask;
  }

  public int getWordHashCode() {
    return myWordHashCode;
  }

  public int getOccurrenceMask() {
    return myOccurrenceMask;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final IdIndexEntry that = (IdIndexEntry)o;

    if (myOccurrenceMask != that.myOccurrenceMask) return false;
    if (myWordHashCode != that.myWordHashCode) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myWordHashCode;
    result = 31 * result + myOccurrenceMask;
    return result;
  }

  @Override
  public String toString() {
    return "IdIndexEntry[hash: " + myWordHashCode + ", mask: " + myOccurrenceMask + "]";
  }
}
