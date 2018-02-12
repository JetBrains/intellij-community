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

import java.util.Collection;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;

@FunctionalInterface
public interface SweepProcessor<T> {
  boolean process(int offset, @NotNull T interval, boolean atStart, @NotNull Collection<T> overlappingIntervals);

  /**
   * Process all intervals from generator in their "start offset - then end offset" order.
   * For each interval call sweepProcessor and pass this interval with its current endpoint (start or end) and current overlapping intervals which this endpoint stabs.
   * E.g. for (0,4), (2,5) intervals call sweepProcessor with (0, empty), (2, 0-4), (4, 2-5), (5, empty)
   */
  static <T extends Segment> boolean sweep(@NotNull Generator<T> generator, @NotNull final SweepProcessor<T> sweepProcessor) {
    Queue<T> ends = new PriorityQueue<>(5, Comparator.comparingInt(Segment::getEndOffset));
    if (!generator.generateInStartOffsetOrder(marker -> {
      // decide whether previous marker ends here or new marker begins
      int start = marker.getStartOffset();
      while (!ends.isEmpty()) {
        T previous = ends.peek();
        int prevEnd = previous.getEndOffset();
        if (prevEnd <= start) {
          if (!sweepProcessor.process(prevEnd, previous, false, ends)) return false;
          ends.remove();
        }
        else {
          break;
        }
      }
      if (!sweepProcessor.process(start, marker, true, ends)) return false;
      ends.offer(marker);

      return true;
    })) return false;

    while (!ends.isEmpty()) {
      T previous = ends.remove();
      int prevEnd = previous.getEndOffset();
      if (!sweepProcessor.process(prevEnd, previous, false, ends)) return false;
    }

    return true;
  }

  @FunctionalInterface
  interface Generator<T> {
    boolean generateInStartOffsetOrder(@NotNull Processor<T> processor);
  }
}
