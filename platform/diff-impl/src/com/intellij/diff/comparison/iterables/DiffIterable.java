/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.comparison.iterables;

import com.intellij.diff.util.Range;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

/*
 * Chunks are guaranteed to be squashed
 * Chunks are not empty
 */
public interface DiffIterable {
  int getLength1();

  int getLength2();

  @NotNull
  Iterator<Range> changes();

  @NotNull
  Iterator<Range> unchanged();

  @NotNull
  Iterable<Range> iterateChanges();

  @NotNull
  Iterable<Range> iterateUnchanged();
}
