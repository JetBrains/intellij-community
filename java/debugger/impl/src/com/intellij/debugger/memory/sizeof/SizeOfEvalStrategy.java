package org.jetbrains.debugger.memory.sizeof;

import com.sun.jdi.ObjectReference;

import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

abstract class SizeOfEvalStrategy {
  public long sizeOf(ObjectReference ref) {
    long startTime = System.nanoTime();
    long result = sizeOfImpl(ref, new HashSet<>(), true);
    long duration = System.nanoTime() - startTime;
    System.out.printf("org.jetbrains.plugin.sizeof [%d] == %d. time = %d ns (%d ms).%n",
        ref.uniqueID(), result, duration, TimeUnit.NANOSECONDS.toMillis(duration));

    return result;
  }

  protected abstract long sizeOfImpl(ObjectReference ref, Collection<ObjectReference> visited, boolean isRoot);
}
