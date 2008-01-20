package com.intellij.psi.impl.cache.index;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 20, 2008
 */
public class TodoIndexEntry {
  final String pattern;
  final boolean caseSensitive;

  public TodoIndexEntry(final String pattern, final boolean caseSensitive) {
    this.pattern = pattern;
    this.caseSensitive = caseSensitive;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final TodoIndexEntry that = (TodoIndexEntry)o;

    if (caseSensitive != that.caseSensitive) return false;
    if (!pattern.equals(that.pattern)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = pattern.hashCode();
    result = 31 * result + (caseSensitive ? 1 : 0);
    return result;
  }
}
