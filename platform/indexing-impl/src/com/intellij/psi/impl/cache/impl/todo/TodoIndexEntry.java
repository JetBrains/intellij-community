/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.psi.impl.cache.impl.todo;

import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public class TodoIndexEntry {
  final String pattern;
  final boolean caseSensitive;

  public TodoIndexEntry(@NotNull String pattern, final boolean caseSensitive) {
    this.pattern = pattern;
    this.caseSensitive = caseSensitive;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final TodoIndexEntry that = (TodoIndexEntry)o;

    return caseSensitive == that.caseSensitive && pattern.equals(that.pattern);
  }

  public int hashCode() {
    int result = pattern.hashCode();
    result = 31 * result + (caseSensitive ? 1 : 0);
    return result;
  }
}
