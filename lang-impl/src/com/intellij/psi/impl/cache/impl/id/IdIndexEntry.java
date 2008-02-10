package com.intellij.psi.impl.cache.impl.id;

import com.intellij.openapi.util.text.StringUtil;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 16, 2008
 */
public final class IdIndexEntry {
  private final int myWordHashCode;

  public IdIndexEntry(String word, boolean caseSensitive) {
    this(caseSensitive? StringUtil.stringHashCode(word) : StringUtil.stringHashCodeInsensitive(word));
  }

  public IdIndexEntry(int wordHash) {
    myWordHashCode = wordHash;
  }

  public int getWordHashCode() {
    return myWordHashCode;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final IdIndexEntry that = (IdIndexEntry)o;

    if (myWordHashCode != that.myWordHashCode) return false;

    return true;
  }

  public int hashCode() {
    return myWordHashCode;
  }

  @Override
  public String toString() {
    return "IdIndexEntry[hash: " + myWordHashCode +"]";
  }
}
