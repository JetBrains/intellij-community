// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.util.Segment;
import com.intellij.util.Processor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;

@ApiStatus.Internal
@FunctionalInterface
public interface SweepProcessor<T> {
  boolean process(int offset, @NotNull T interval, boolean atStart, @NotNull Collection<? extends T> overlappingIntervals);

  /**
   * Process all intervals from the {@code generator} in their "start offset; then end offset" order.
   * For each interval call {@code sweepProcessor} and pass this interval, its current endpoint (start or end), and current overlapping intervals which this endpoint stabs.
   * E.g. for (0,4), (2,5) intervals, this method will call {@code sweepProcessor} with:<pre>
   *   (offset=0, atStart=true,  overlapping=empty),
   *   (offset=2, atStart=true,  overlapping=(0-4)),
   *   (offset=4, atStart=false, overlapping=(2-5),
   *   (offset=5, atStart=false, overlapping=empty)
   * </pre>
   * To maintain the correct order the {@code generator} must supply intervals in their {@link Segment#getStartOffset()} order.
   */
  static <T extends Segment> boolean sweep(@NotNull Generator<? extends T> generator, @NotNull SweepProcessor<T> sweepProcessor) {
    Queue<T> ends = new PriorityQueue<>(5, Comparator.comparingInt(Segment::getEndOffset));
    if (!generator.processAllInStartOffsetOrder(marker -> {
      // decide whether the previous marker ends here or the new marker begins
      int start = marker.getStartOffset();
      while (!ends.isEmpty()) {
        T previous = ends.peek();
        int previousStartOffset = previous.getStartOffset();
        if (start < previousStartOffset) {
          throw new IllegalStateException("Generator "+generator+" supplied segments in a wrong order: "+previous+" was received before "+ marker +" ("+start+"<"+previousStartOffset+")");
        }
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

  @ApiStatus.Internal
  @FunctionalInterface
  interface Generator<T> {
    boolean processAllInStartOffsetOrder(@NotNull Processor<? super T> processor);
  }
}
