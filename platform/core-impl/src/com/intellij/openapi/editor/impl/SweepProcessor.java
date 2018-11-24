/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.util.Segment;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@FunctionalInterface
public interface SweepProcessor<T> {
  boolean process(int offset, @NotNull T interval, boolean atStart, @NotNull Collection<T> overlappingIntervals);

  static <T extends Segment> boolean sweep(@NotNull Generator<T> generator, @NotNull final SweepProcessor<T> sweepProcessor) {
    final Queue<T> ends = new PriorityQueue<>(5, Comparator.comparingInt(Segment::getEndOffset));
    final List<T> starts = new ArrayList<>();
    if (!generator.generateInStartOffsetOrder(marker -> {
      // decide whether previous marker ends here or new marker begins
      int start = marker.getStartOffset();
      while (true) {
        assert ends.size() == starts.size();
        T previous = ends.peek();
        if (previous != null) {
          int prevEnd = previous.getEndOffset();
          if (prevEnd <= start) {
            if (!sweepProcessor.process(prevEnd, previous, false, ends)) return false;
            ends.remove();
            boolean removed = starts.remove(previous);
            assert removed;
            continue;
          }
        }
        break;
      }
      if (!sweepProcessor.process(start, marker, true, ends)) return false;
      starts.add(marker);
      ends.offer(marker);

      return true;
    })) return false;

    while (!ends.isEmpty()) {
      assert ends.size() == starts.size();
      T previous = ends.remove();
      int prevEnd = previous.getEndOffset();
      if (!sweepProcessor.process(prevEnd, previous, false, ends)) return false;
      boolean removed = starts.remove(previous);
      assert removed;
    }

    return true;
  }

  @FunctionalInterface
  interface Generator<T> {
    boolean generateInStartOffsetOrder(@NotNull Processor<T> processor);
  }
}
