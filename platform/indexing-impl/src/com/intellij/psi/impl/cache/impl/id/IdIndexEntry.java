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

package com.intellij.psi.impl.cache.impl.id;

import com.intellij.find.ngrams.TrigramIndex;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class IdIndexEntry {
  private static final boolean ourUseStrongerHash =
    !TrigramIndex.isEnabled() || SystemProperties.getBooleanProperty("idea.id.index.use.stronger.hash", true);
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
    return "IdIndexEntry[hash: " + myWordHashCode +"]";
  }

  static int getWordHash(@NotNull CharSequence line, int start, int end, boolean caseSensitive) {
    if (useStrongerHash()) {
      // use stronger hash
      return caseSensitive ? StringUtil.stringHashCode(line, start, end) : StringUtil.stringHashCodeInsensitive(line, start, end);
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
      return (firstChar << 8) + (lastChar << 4) + end - start;
    }
  }

  static int getUsedHashAlgorithmVersion() {
    return useStrongerHash() ? 1 : 0;
  }

  @ApiStatus.Internal
  public static boolean useStrongerHash() {
    return ourUseStrongerHash;
  }
}
