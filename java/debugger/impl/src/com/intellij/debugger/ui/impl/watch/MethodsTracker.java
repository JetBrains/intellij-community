// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.impl.watch;

import com.intellij.util.ThreeState;
import com.sun.jdi.Method;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 */
public class MethodsTracker {
  @SuppressWarnings("SSBasedInspection") private final Object2IntOpenHashMap<Method> myMethodCounter = new Object2IntOpenHashMap<>();
  private final Int2ObjectMap<MethodOccurrence> myCache = new Int2ObjectOpenHashMap<>();

  public final class MethodOccurrence {
    private final @Nullable Method myMethod;
    private final int myIndex;
    private final int myFrameIndex;

    private MethodOccurrence(@Nullable Method method, int index, int frameIndex) {
      myMethod = method;
      myIndex = index;
      myFrameIndex = frameIndex;
    }

    public @Nullable Method getMethod() {
      return myMethod;
    }

    public int getIndex() {
      return getOccurrenceCount(myMethod) - myIndex;
    }

    public boolean isRecursive() {
      return myMethod != null && getOccurrenceCount(myMethod) > 1;
    }

    // check that all methods are not native to be able to drop a frame
    ThreeState canDrop() {
      for (int i = 0; i <= myFrameIndex + 1; i++) {
        MethodOccurrence occurrence = myCache.get(i);
        Method method = occurrence != null ? occurrence.myMethod : null;
        if (method == null && i == myFrameIndex + 1) return ThreeState.UNSURE;
        if (method == null || method.isNative()) {
          return ThreeState.NO;
        }
      }
      return ThreeState.YES;
    }
  }

  public MethodOccurrence getMethodOccurrence(int frameIndex, @Nullable Method method) {
    return myCache.computeIfAbsent(frameIndex, __ -> {
      synchronized (myMethodCounter) {
        int occurrence = method != null ? myMethodCounter.addTo(method, 1) : 0;
        return new MethodOccurrence(method, occurrence, frameIndex);
      }
    });
  }

  private int getOccurrenceCount(@Nullable Method method) {
    synchronized (myMethodCounter) {
      return myMethodCounter.getInt(method);
    }
  }
}
