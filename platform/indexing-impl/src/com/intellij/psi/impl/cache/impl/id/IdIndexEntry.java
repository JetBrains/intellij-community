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

import com.intellij.openapi.util.text.StringUtil;

/**
 * @author Eugene Zhuravlev
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
