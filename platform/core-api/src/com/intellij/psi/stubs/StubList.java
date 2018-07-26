// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IntIntFunction;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A storage for stub-related data, shared by all stubs in one file. More memory-efficient, than keeping the same data in stub objects themselves.
 */
class StubList {
  /** The list of all stubs ordered by id. The order is DFS (except maybe temporarily during construction, fixed by {@link #finalizeLoadingStage()} later) */
  private final ArrayList<StubBase<?>> myPlainList;

  /** A list to hold ids of stub children at contiguous ranges, to avoid allocating separate lists in each parent stub */
  private final MostlyUShortIntList myJoinedChildrenList;

  /** Means that the children should be found in {@link TempState#myTempJoinedChildrenMap} */
  private static final int IN_TEMP_MAP = -1;

  /**
   * Holds data for the stub with the given id.
   * For each id there's 3 values:
   * <ol>
   *   <li>element type id</li>
   *   <li>children start: 0 when children are found in {@link #myPlainList} right after parent id, a positive integer for an offset in {@link #myJoinedChildrenList} where the children start, or {@link #IN_TEMP_MAP}</li>
   *   <li>children count</li>
   * </ol>
   */
  private final MostlyUShortIntList myStubData;

  @Nullable private TempState myTempState = new TempState();

  StubList(int initialCapacity) {
    myPlainList = new ArrayList<>(initialCapacity);
    myStubData = new MostlyUShortIntList(initialCapacity * 3);
    myJoinedChildrenList = new MostlyUShortIntList(initialCapacity);
    myJoinedChildrenList.add(0); // indices in this list should be non-zero 
  }

  IStubElementType<?, ?> getStubType(int id) {
    return (IStubElementType<?, ?>)IElementType.find(getStubTypeIndex(id));
  }

  private short getStubTypeIndex(int id) {
    return (short)myStubData.get(id * 3);
  }

  private static int childrenStartIndex(int id) {
    return id * 3 + 1;
  }

  private static int childrenCountIndex(int id) {
    return id * 3 + 2;
  }

  private int getChildrenStart(int id) {
    return myStubData.get(childrenStartIndex(id));
  }

  private int getChildrenCount(int id) {
    return myStubData.get(childrenCountIndex(id));
  }

  void addStub(@NotNull StubBase<?> stub, @Nullable StubBase<?> parent, @Nullable IStubElementType<?, ?> type) {
    int stubId = myPlainList.size();
    setStubToListReferences(stub, stubId);

    int parentId = parent == null ? -1 : parent.id;
    if (nonDfsOrderDetected(parentId, stubId)) {
      Objects.requireNonNull(myTempState).switchChildrenToTempMap(parentId);
    }

    addStub(stub, stubId, parentId, type == null ? 0 : type.getIndex());
  }

  private boolean nonDfsOrderDetected(int parentId, int childId) {
    return parentId >= 0 && childId != parentId + 1 && getChildrenCount(parentId) == 0;
  }

  private void setStubToListReferences(@NotNull StubBase<?> stub, int stubId) {
    stub.myStubList = this;
    stub.id = stubId;
  }

  private void addStub(@NotNull StubBase<?> child, int childId, int parentId, short elementTypeIndex) {
    assert myTempState != null;
    assert childId == myPlainList.size();

    myPlainList.add(child);
    myStubData.add(elementTypeIndex); myStubData.add(0); myStubData.add(0);

    if (parentId < 0) return;

    int childrenCount = getChildrenCount(parentId);
    int childrenStart = myTempState.ensureCapacityForNextChild(childId, parentId, childrenCount);

    ChildrenStorage storage = getChildrenStorage(childrenStart);
    if (storage == ChildrenStorage.inJoinedList) {
      addToJoinedChildren(childrenStart + childrenCount, childId);
    }
    else if (storage == ChildrenStorage.inTempMap) {
      tempMap().get(parentId).add(childId);
    }

    myStubData.set(childrenCountIndex(parentId), childrenCount + 1);
  }

  private enum ChildrenStorage { inPlainList, inJoinedList, inTempMap }

  private static ChildrenStorage getChildrenStorage(int childrenStart) {
    return childrenStart == 0 ? ChildrenStorage.inPlainList :
           childrenStart == IN_TEMP_MAP ? ChildrenStorage.inTempMap :
           ChildrenStorage.inJoinedList;
  }

  private boolean canAddToJoinedList(int index) {
    return myJoinedChildrenList.size() == index || myJoinedChildrenList.get(index) == 0;
  }

  private void addToJoinedChildren(int index, int childId) {
    if (myJoinedChildrenList.size() == index) {
      myJoinedChildrenList.add(childId);
    }
    else {
      assert myJoinedChildrenList.get(index) == 0;
      myJoinedChildrenList.set(index, childId);
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
      case inJoinedList: return idSubList(myJoinedChildrenList, start, count);
      default: return idSubList(tempMap().get(id), 0, count);
    }
  }

  private List<StubBase<?>> idSubList(MostlyUShortIntList idList, int start, int count) {
    return new AbstractList<StubBase<?>>() {
      @Override
      public StubBase<?> get(int index) {
        return myPlainList.get(idList.get(start + index));
      }

      @Override
      public int size() {
        return count;
      }
    };
  }

  private TIntObjectHashMap<MostlyUShortIntList> tempMap() {
    assert myTempState != null;
    return Objects.requireNonNull(myTempState.myTempJoinedChildrenMap);
  }

  @Nullable
  <P extends PsiElement, S extends StubElement<P>> S findChildStubByType(int id, @NotNull IStubElementType<S, P> elementType) {
    int count = getChildrenCount(id);
    int start = getChildrenStart(id);
    switch (getChildrenStorage(start)) {
      case inPlainList: return findChildStubByType(elementType, IntIntFunction.IDENTITY, id + 1, id + 1 + count);
      case inJoinedList: return findChildStubByType(elementType, myJoinedChildrenList, start, start + count);
      default: return findChildStubByType(elementType, Objects.requireNonNull(tempMap()).get(id), 0, count);
    }
  }

  @Nullable
  private <P extends PsiElement, S extends StubElement<P>> S findChildStubByType(IStubElementType<S, P> elementType,
                                                                                 IntIntFunction idList,
                                                                                 int start, int end) {
    for (int i = start; i < end; ++i) {
      int id = idList.fun(i);
      if (elementType.getIndex() == getStubTypeIndex(id)) {
        //noinspection unchecked
        return (S)myPlainList.get(id);
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
    StubList copy = new StubList(myPlainList.size());
    new Object() {
      void visitStub(StubBase<?> stub, int parentId) {
        int idInCopy = copy.myPlainList.size();
        copy.addStub(stub, idInCopy, parentId, getStubTypeIndex(stub.id));

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
    @Nullable TIntObjectHashMap<MostlyUShortIntList> myTempJoinedChildrenMap;

    int myCurrentParent = -1;
    int myExpectedChildrenCount;

    int ensureCapacityForNextChild(int childId, int parentId, int childrenCount) {
      if (myCurrentParent >= 0) {
        if (childrenCount == myExpectedChildrenCount - 1) {
          myCurrentParent = -1;
        } else if (parentId != myCurrentParent) {
          myCurrentParent = -1;
          return switchChildrenToJoinedList(parentId, childrenCount, myExpectedChildrenCount - childrenCount);
        }
      }

      int childrenStart = getChildrenStart(parentId);
      ChildrenStorage storage = getChildrenStorage(childrenStart);
      if (storage == ChildrenStorage.inPlainList) {
        if (childrenCount == 0) {
          assert parentId == childId - 1;
        }
        else if (areChildrenNonAdjacent(childId, parentId)) {
          return switchChildrenToJoinedList(parentId, childrenCount, 0);
        }
      }
      else if (storage == ChildrenStorage.inJoinedList && !canAddToJoinedList(childrenStart + childrenCount)) {
        switchChildrenToTempMap(parentId);
        return IN_TEMP_MAP;
      }
      return childrenStart;
    }

    private boolean areChildrenNonAdjacent(int childId, int parentId) {
      return myPlainList.get(childId - 1).getParentStub() != myPlainList.get(parentId);
    }

    private int switchChildrenToJoinedList(int parentId, int childrenCount, int slotsToReserve) {
      int start = myJoinedChildrenList.size();
      assert start > 0;
      for (int i = 0; i < childrenCount; i++) {
        myJoinedChildrenList.add(parentId + i + 1);
      }
      for (int i = 0; i < slotsToReserve; i++) {
        myJoinedChildrenList.add(0);
      }
      myStubData.set(childrenStartIndex(parentId), start);
      return start;
    }

    private void switchChildrenToTempMap(int parentId) {
      if (myTempJoinedChildrenMap == null) myTempJoinedChildrenMap = new TIntObjectHashMap<>();

      int start = getChildrenStart(parentId);
      int count = getChildrenCount(parentId);
      MostlyUShortIntList ids = new MostlyUShortIntList(count + 1);
      switch (getChildrenStorage(start)) {
        case inPlainList:
          for (int i = 0; i < count; i++) ids.add(parentId + i + 1);
          break;
        case inJoinedList:
          for (int i = start; i < start + count; i++) ids.add(myJoinedChildrenList.get(i));
          break;
        default: throw new IllegalStateException();
      }

      MostlyUShortIntList prev = myTempJoinedChildrenMap.put(parentId, ids);
      assert prev == null;

      myStubData.set(childrenStartIndex(parentId), IN_TEMP_MAP);
    }

    void prepareForChildren(int parentId, int childrenCount) {
      assert parentId == myPlainList.size() - 1;
      if (childrenCount == 0) return;

      if (myCurrentParent >= 0) {
        int currentCount = getChildrenCount(myCurrentParent);
        switchChildrenToJoinedList(myCurrentParent, currentCount, myExpectedChildrenCount - currentCount);
      }

      myCurrentParent = parentId;
      myExpectedChildrenCount = childrenCount;
    }

  }
  
}
