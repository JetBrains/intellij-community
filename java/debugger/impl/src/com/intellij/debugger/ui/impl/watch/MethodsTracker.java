// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.impl.watch;

import com.sun.jdi.Method;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * @author Eugene Zhuravlev
 */
public class MethodsTracker {
  @SuppressWarnings("SSBasedInspection") private final Object2IntOpenHashMap<Method> myMethodCounter = new Object2IntOpenHashMap<>();
  private final Int2ObjectMap<MethodOccurrence> myCache = new Int2ObjectOpenHashMap<>();
  private final CompletableFuture<Void> myFinished = new CompletableFuture<>();

  public final class MethodOccurrence {
    private final @Nullable Method myMethod;
    private final int myIndex;

    private MethodOccurrence(@Nullable Method method, int index) {
      myMethod = method;
      myIndex = index;
    }

    public @Nullable Method getMethod() {
      return myMethod;
    }

    /**
     * @return recursion index, or 0 if this is not a recursive call
     */
    public CompletableFuture<Integer> getExactRecursiveIndex() {
      if (myMethod == null) {
        return CompletableFuture.completedFuture(0);
      }
      return getExactOccurrenceCount(myMethod).thenApply(occurrenceCount -> {
                                                           if (occurrenceCount <= 1) return -1;
                                                           return occurrenceCount - myIndex;
                                                         }
      );
    }

    public int getIndex() {
      return getOccurrenceCount(myMethod) - myIndex;
    }

    public boolean isRecursive() {
      return myMethod != null && getOccurrenceCount(myMethod) > 1;
    }

    MethodOccurrence getMethodOccurrence(int frameIndex) {
      return myCache.get(frameIndex);
    }
  }

  public void finish() {
    myFinished.complete(null);
  }

  public MethodOccurrence getMethodOccurrence(int frameIndex, @Nullable Method method) {
    return myCache.computeIfAbsent(frameIndex, __ -> {
      synchronized (myMethodCounter) {
        int occurrence = method != null ? myMethodCounter.addTo(method, 1) : 0;
        return new MethodOccurrence(method, occurrence);
      }
    });
  }

  private int getOccurrenceCount(@Nullable Method method) {
    synchronized (myMethodCounter) {
      return myMethodCounter.getInt(method);
    }
  }

  private CompletableFuture<Integer> getExactOccurrenceCount(@Nullable Method method) {
    return myFinished.thenApply(__ -> myMethodCounter.getInt(method));
  }
}
