// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.UnsignedShortArrayList;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A storage for stub-related data, shared by all stubs in one file. More memory-efficient, than keeping the same data in stub objects themselves.
 */
class StubList {
  /** The list of all stubs ordered by id. The order is DFS (except maybe temporarily during construction, fixed by {@link #finalizeLoadingStage()} later) */
  private final ArrayList<StubBase<?>> myPlainList = new ArrayList<>();
  
  /** A list to hold stub children at contiguous ranges, to avoid allocating separate lists in each parent stub */
  private final ArrayList<StubBase<?>> myJoinedChildrenList = new ArrayList<>();

  /** Means that the children should be found in {@link TempState#myTempJoinedChildrenMap} */
  private static final int IN_TEMP_MAP = Character.MAX_VALUE;
  
  /** Means that a value doesn't fit 2 bytes and should be found in {@link #myLargeCounts} or {@link #myLargeStarts} */
  private static final int IN_LARGE_MAP = IN_TEMP_MAP - 1;
  
  private static final int MAX_SHORT_VALUE = IN_LARGE_MAP - 1;
  
  /**
   * Holds data for the stub with the given id.
   * For each id there's 3 shorts:
   * <ol>
   *   <li>element type id</li>
   *   <li>children start: 0 when children are found in {@link #myPlainList} right after parent id, another number for an offset in {@link #myJoinedChildrenList} where the children start, or {@link #IN_TEMP_MAP} or {@link #IN_LARGE_MAP}</li>
   *   <li>children count or {@link #IN_LARGE_MAP} if doesn't fit</li>
   * </ol>
   */
  private final UnsignedShortArrayList myStubData = new UnsignedShortArrayList();
  
  private TIntIntHashMap myLargeStarts = null;
  private TIntIntHashMap myLargeCounts = null;

  @Nullable private TempState myTempState = new TempState();

  StubList() {
    myJoinedChildrenList.add(null); // indices in this list should be non-zero 
  }

  IStubElementType<?, ?> getStubType(int id) {
    return (IStubElementType<?, ?>)IElementType.find((short)myStubData.getQuick(id * 3));
  }

  private int getChildrenStart(int id) {
    int start = myStubData.getQuick(id * 3 + 1);
    return start == IN_LARGE_MAP ? myLargeStarts.get(id) : start;
  }

  private int getChildrenCount(int id) {
    int count = myStubData.getQuick(id * 3 + 2);
    return count == IN_LARGE_MAP ? myLargeCounts.get(id) : count;
  }

  private void setChildrenStart(int id, int start) {
    if (start > MAX_SHORT_VALUE) {
      if (myLargeStarts == null) myLargeStarts = new TIntIntHashMap();
      myLargeStarts.put(id, start);
      start = IN_LARGE_MAP;
    }
    myStubData.setQuick(id * 3 + 1, start);
  }

  private void setChildrenCount(int id, int count) {
    if (count > MAX_SHORT_VALUE) {
      if (myLargeCounts == null) myLargeCounts = new TIntIntHashMap();
      myLargeCounts.put(id, count);
      count = IN_LARGE_MAP;
    }
    
    myStubData.setQuick(id * 3 + 2, count);
  }

  void addStub(@NotNull StubBase<?> stub, @Nullable StubBase<?> parent, @Nullable IStubElementType<?, ?> type) {
    int stubId = myPlainList.size();
    setStubToListReferences(stub, stubId);
    addStub(stub, parent, stubId, parent == null ? -1 : parent.id, type);
  }

  private void setStubToListReferences(@NotNull StubBase<?> stub, int stubId) {
    stub.myStubList = this;
    stub.id = stubId;
  }

  private void addStub(@NotNull StubBase<?> child, @Nullable StubBase<?> parent, int childId, int parentId, @Nullable IStubElementType<?, ?> type) {
    assert myTempState != null;
    assert childId == myPlainList.size();

    myPlainList.add(child);

    myStubData.add(type == null ? 0 : type.getIndex());
    myStubData.add(0);
    myStubData.add(0);
    
    if (parent == null) return;

    int childrenCount = getChildrenCount(parentId);
    int childrenStart = myTempState.ensureCapacityForNextChild(childId, parentId, childrenCount, parent);

    ChildrenStorage storage = getChildrenStorage(childrenStart);
    if (storage == ChildrenStorage.inJoinedList) {
      addToJoinedChildren(child, childrenStart + childrenCount);
    }
    else if (storage == ChildrenStorage.inTempMap) {
      tempMap().get(parentId).add(child);
    }

    setChildrenCount(parentId, childrenCount + 1);
  }
  
  private enum ChildrenStorage { inPlainList, inJoinedList, inTempMap }

  private static ChildrenStorage getChildrenStorage(int childrenStart) {
    return childrenStart == 0 ? ChildrenStorage.inPlainList :
           childrenStart <= MAX_SHORT_VALUE ? ChildrenStorage.inJoinedList :
           ChildrenStorage.inTempMap;
  }

  private boolean canAddToJoinedList(int index) {
    return myJoinedChildrenList.size() == index || myJoinedChildrenList.get(index) == null;
  }

  private void addToJoinedChildren(StubBase<?> child, int index) {
    if (myJoinedChildrenList.size() == index) {
      myJoinedChildrenList.add(child);
    }
    else if (myJoinedChildrenList.get(index) == null) {
      myJoinedChildrenList.set(index, child);
    }
  }

  void prepareForChildren(StubBase<?> parent, int childrenCount) {
    assert myTempState != null;
    myTempState.prepareForChildren(parent.id, childrenCount);
  }

  List<StubBase<?>> getChildrenStubs(int id) {
    int count = getChildrenCount(id);
    if (count == 0) return Collections.emptyList();

    int start = getChildrenStart(id);
    switch (getChildrenStorage(start)) {
      case inPlainList: return myPlainList.subList(id + 1, id + 1 + count);
      case inJoinedList: return myJoinedChildrenList.subList(start, start + count);
      default: return tempMap().get(id);
    }
  }

  private TIntObjectHashMap<List<StubBase<?>>> tempMap() {
    assert myTempState != null;
    return Objects.requireNonNull(myTempState.myTempJoinedChildrenMap);
  }

  @Nullable
  <P extends PsiElement, S extends StubElement<P>> S findChildStubByType(int id, @NotNull IStubElementType<S, P> elementType) {
    int count = getChildrenCount(id);
    int start = getChildrenStart(id);
    switch (getChildrenStorage(start)) {
      case inPlainList: return findChildStubByType(elementType, myPlainList, id + 1, id + 1 + count);
      case inJoinedList: return findChildStubByType(elementType, myJoinedChildrenList, start, start + count);
      default: return findChildStubByType(elementType, Objects.requireNonNull(tempMap()).get(id), 0, count);
    }
  }

  @Nullable
  private static <P extends PsiElement, S extends StubElement<P>> S findChildStubByType(IStubElementType<S, P> elementType,
                                                                                        List<StubBase<?>> childrenStubs,
                                                                                        int start, int end) {
    for (int i = start; i < end; ++i) {
      StubElement childStub = childrenStubs.get(i);
      if (childStub.getStubType() == elementType) {
        //noinspection unchecked
        return (S)childStub;
      }
    }
    return null;
  }

  /** 
   * Ensures stubs are in DFS order and the optimizes memory layout. Might return an optimized copy of this list,
   * with all stubs re-targeted to that copy.
   */
  @NotNull
  StubList finalizeLoadingStage() {
    if (myTempState == null) return this;
    
    if (!isChildrenLayoutOptimal()) {
      return createOptimizedCopy();
    }
    
    myTempState = null;
    myPlainList.trimToSize();
    myJoinedChildrenList.trimToSize();
    myStubData.trimToSize();
    return this;
  }

  @NotNull
  List<StubElement<?>> toPlainList() {
    return Collections.unmodifiableList(myPlainList);
  }

  @NotNull
  private StubList createOptimizedCopy() {
    StubList copy = new StubList();
    new Object() {
      void visitStub(StubBase<?> stub, int parentId) {
        int idInCopy = copy.myPlainList.size();
        copy.addStub(stub, (StubBase<?>)stub.getParentStub(), idInCopy, parentId, stub.getStubType());

        List<StubBase<?>> children = getChildrenStubs(stub.id);
        Objects.requireNonNull(copy.myTempState).prepareForChildren(idInCopy, children.size());

        for (StubBase<?> child : children) {
          visitStub(child, idInCopy);
        }
      }
    }.visitStub(myPlainList.get(0), -1);

    assert copy.isChildrenLayoutOptimal();

    for (int i = 0; i < copy.myPlainList.size(); i++) {
      copy.setStubToListReferences(copy.myPlainList.get(i), i);
    }

    return copy.finalizeLoadingStage();
  }

  boolean isChildrenLayoutOptimal() {
    return myTempState == null || myTempState.myTempJoinedChildrenMap == null;
  }

  private class TempState {
    @Nullable TIntObjectHashMap<List<StubBase<?>>> myTempJoinedChildrenMap;

    int myCurrentParent = -1;
    int myExpectedChildrenCount;

    int ensureCapacityForNextChild(int childId, int parentId, int childrenCount, StubBase<?> parent) {
      if (myCurrentParent >= 0) {
        if (childrenCount == myExpectedChildrenCount - 1) {
          myCurrentParent = -1;
        } else if (parentId != myCurrentParent) {
          myCurrentParent = -1;
          return switchChildrenToJoinedList(parentId, myExpectedChildrenCount - childrenCount);
        }
      }

      int childrenStart = getChildrenStart(parentId);
      ChildrenStorage storage = getChildrenStorage(childrenStart);
      if (childrenCount == 0) {
        assert storage == ChildrenStorage.inPlainList;
        if (parentId != childId - 1) { // stubs created in non-DFS order
          switchChildrenToTempMap(parentId);
          return IN_TEMP_MAP;
        }
      }
      else if (storage == ChildrenStorage.inPlainList && areChildrenNonAdjacent(childId, parent)) {
        return switchChildrenToJoinedList(parentId, 0);
      }
      else if (storage == ChildrenStorage.inJoinedList && !canAddToJoinedList(childrenStart + childrenCount)) {
        switchChildrenToTempMap(parentId);
        return IN_TEMP_MAP;
      }
      return childrenStart;
    }

    private boolean areChildrenNonAdjacent(int childId, StubBase<?> parent) {
      return myPlainList.get(childId - 1).getParentStub() != parent;
    }

    private int switchChildrenToJoinedList(int parentId, int slotsToReserve) {
      int start = myJoinedChildrenList.size();
      assert start > 0;
      myJoinedChildrenList.addAll(getChildrenStubs(parentId));
      setChildrenStart(parentId, start);

      for (int i = 0; i < slotsToReserve; i++) {
        myJoinedChildrenList.add(null);
      }
      return start;
    }

    private void switchChildrenToTempMap(int parentId) {
      assert myTempJoinedChildrenMap == null || !myTempJoinedChildrenMap.containsKey(parentId);

      if (myTempJoinedChildrenMap == null) myTempJoinedChildrenMap = new TIntObjectHashMap<>();
      myTempJoinedChildrenMap.put(parentId, new ArrayList<>(getChildrenStubs(parentId)));

      setChildrenStart(parentId, IN_TEMP_MAP);
    }

    void prepareForChildren(int parentId, int childrenCount) {
      assert parentId == myPlainList.size() - 1;
      if (childrenCount == 0) return;

      if (myCurrentParent >= 0) {
        switchChildrenToJoinedList(myCurrentParent, myExpectedChildrenCount - getChildrenCount(myCurrentParent));
      }

      myCurrentParent = parentId;
      myExpectedChildrenCount = childrenCount;
    }

  }
  
}
