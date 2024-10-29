// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.cache.impl.id;

import com.intellij.find.ngrams.TrigramIndex;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.SystemProperties.getBooleanProperty;

/**
 * Entry of {@link IdIndex}.
 * Conceptually it represents a single identifier from a code file, e.g. class or variable name.
 * Identifiers could be case-sensitive/insensitive, depending on source language rules.
 * <p>
 * Implementation-wise, for memory-efficiency, the identifier itself is not stored -- it's hash code (=int32) stored instead.
 * That opens a possibility of collisions -- i.e. IdIndex lookup could return 'false positives', i.e. files that really don't
 * contain identifier queried, but contain another identifier with the same hash instead. Which is fine, since we always re-check
 * all the files found by IdIndex to really contain requested identifier.
 * <p>
 * In current implementation case-sensitive hash uses algo == {@link String#hashCode()}, while case-insensitive is the same but
 * string is converted to lower-case first, see {@link #getWordHash(CharSequence, int, int, boolean)}
 */
@ApiStatus.Internal
public final class IdIndexEntry {
  private static final boolean USE_STRONGER_HASH = !TrigramIndex.isEnabled() || getBooleanProperty("idea.id.index.use.stronger.hash", true);

  private final int myWordHashCode;

  public IdIndexEntry(@NotNull String word, boolean caseSensitive) {
    this(word, 0, word.length(), caseSensitive);
  }

  public IdIndexEntry(@NotNull CharSequence seq, int start, int end, boolean caseSensitive) {
    this(getWordHash(seq, start, end, caseSensitive));
  }

  public IdIndexEntry(int wordHash) {
    myWordHashCode = wordHash;
  }

  public int getWordHashCode() {
    return myWordHashCode;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final IdIndexEntry that = (IdIndexEntry)o;

    if (myWordHashCode != that.myWordHashCode) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myWordHashCode;
  }

  @Override
  public String toString() {
    return "IdIndexEntry[hash: " + myWordHashCode + "]";
  }

  static int getWordHash(@NotNull CharSequence line, int start, int end, boolean caseSensitive) {
    if (useStrongerHash()) {
      return caseSensitive ? StringUtil.stringHashCode(line, start, end)
                           : StringUtil.stringHashCodeInsensitive(line, start, end);
    }
    else {
      // use more compact hash
      if (start == end) return 0;
      char firstChar = line.charAt(start);
      char lastChar = line.charAt(end - 1);
      if (!caseSensitive) {
        firstChar = StringUtil.toLowerCase(firstChar);
        lastChar = StringUtil.toLowerCase(lastChar);
      }
      //TODO RC: Why start/end _positions_ in line are taken as a part of the hash?
      //         It means that same identifier has different hash depending on it's position in line?
      return (firstChar << 8) + (lastChar << 4) + end - start;
    }
  }

  static int getUsedHashAlgorithmVersion() {
    return useStrongerHash() ? 1 : 0;
  }

  @ApiStatus.Internal
  public static boolean useStrongerHash() {
    return USE_STRONGER_HASH;
  }
}
