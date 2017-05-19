/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ChangedRangesInfo {

  private final List<TextRange> insertedRanges;
  private final List<TextRange> allChangedRanges;

  public ChangedRangesInfo(@NotNull List<TextRange> allChangedRanges, @Nullable List<TextRange> insertedRanges) {
    this.insertedRanges = insertedRanges;
    this.allChangedRanges = allChangedRanges;
  }

  public List<TextRange> insertedRanges() {
    return insertedRanges;
  }

  public List<TextRange> optimizedChangedRanges() {
    if (allChangedRanges.isEmpty()) return allChangedRanges;
    allChangedRanges.sort(Segment.BY_START_OFFSET_THEN_END_OFFSET);

    List<TextRange> result = ContainerUtil.newSmartList();

    TextRange prev = allChangedRanges.get(0);
    for (TextRange next : allChangedRanges) {
      if (next.getStartOffset() <= prev.getEndOffset() + 5) {
        int newEndOffset = Math.max(prev.getEndOffset(), next.getEndOffset());
        prev = new TextRange(prev.getStartOffset(), newEndOffset);
      }
      else {
        result.add(prev);
        prev = next;
      }
    }
    result.add(prev);

    return result;
  }
  
}
