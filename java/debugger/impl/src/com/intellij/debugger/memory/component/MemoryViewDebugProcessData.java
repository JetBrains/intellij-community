// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.component;

import com.intellij.debugger.memory.utils.StackFrameItem;
import com.intellij.openapi.util.Key;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Vitaliy.Bibaev
 */
public class MemoryViewDebugProcessData {
  public static final Key<MemoryViewDebugProcessData> KEY = Key.create("MemoryView.DebugProcessData");

  private final TrackedStacksContainer myStacksContainer = new MyStackContainer();

  public @NotNull TrackedStacksContainer getTrackedStacks() {
    return myStacksContainer;
  }

  private static class MyStackContainer implements TrackedStacksContainer {
    private final Map<ReferenceType, Map<ObjectReference, List<StackFrameItem>>> myType2Reference2Stack = new ConcurrentHashMap<>();

    private final Map<ReferenceType, Map<ObjectReference, List<StackFrameItem>>> myPinnedType2Reference2Stack = new ConcurrentHashMap<>();

    @Override
    public @Nullable List<StackFrameItem> getStack(@NotNull ObjectReference reference) {
      final List<StackFrameItem> stack = extract(myType2Reference2Stack, reference);
      return stack != null ? stack : extract(myPinnedType2Reference2Stack, reference);
    }

    @Override
    public void addStack(@NotNull ObjectReference ref, @NotNull List<StackFrameItem> frames) {
      myType2Reference2Stack.computeIfAbsent(ref.referenceType(), referenceType -> new ConcurrentHashMap<>()).put(ref, frames);
    }

    @Override
    public void pinStacks(@NotNull ReferenceType referenceType) {
      final Map<ObjectReference, List<StackFrameItem>> ref2Stack = myType2Reference2Stack.get(referenceType);
      if (ref2Stack != null) {
        myPinnedType2Reference2Stack.put(referenceType, ref2Stack);
      }
    }

    @Override
    public void unpinStacks(@NotNull ReferenceType referenceType) {
      myPinnedType2Reference2Stack.remove(referenceType);
    }

    @Override
    public void release() {
      myType2Reference2Stack.clear();
    }

    @Override
    public void clear() {
      release();
      myPinnedType2Reference2Stack.clear();
    }

    private static @Nullable List<StackFrameItem> extract(@NotNull Map<ReferenceType, Map<ObjectReference, List<StackFrameItem>>> map,
                                                          @NotNull ObjectReference ref) {
      final Map<ObjectReference, List<StackFrameItem>> ref2Stack = map.get(ref.referenceType());
      return ref2Stack != null ? ref2Stack.get(ref) : null;
    }
  }
}
