// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntObjectMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.function.IntUnaryOperator;

/**
 * A storage for stub-related data, shared by all stubs in one file. More memory-efficient, than keeping the same data in stub objects themselves.
 */
@ApiStatus.Internal
public abstract class StubList extends AbstractList<StubBase<?>> {
  /** A list to hold ids of stub children at contiguous ranges, to avoid allocating separate lists in each parent stub */
  private final MostlyUShortIntList myJoinedChildrenList;

  /** Means that the children should be found in {@link TempState#myTempJoinedChildrenMap} */
  private static final int IN_TEMP_MAP = -1;

  /**
   * Holds data for the stub with the given id.
   * For each id there's 3 values:
   * <ol>
   *   <li>element type id</li>
   *   <li>children start: 0 when children are found in {@link #toPlainList()} right after parent id, a positive integer for an offset in {@link #myJoinedChildrenList} where the children start, or {@link #IN_TEMP_MAP}</li>
   *   <li>children count</li>
   * </ol>
   */
  private final MostlyUShortIntList myStubData;

  private @Nullable TempState myTempState = new TempState();

  public StubList(int initialCapacity) {
    myStubData = new MostlyUShortIntList(initialCapacity * 3);
    myJoinedChildrenList = new MostlyUShortIntList(initialCapacity);
    myJoinedChildrenList.add(0); // indices in this list should be non-zero
  }

  public @NotNull IElementType getStubElementType(int id) {
    return IElementType.find(getStubTypeIndex(id));
  }

  short getStubTypeIndex(int id) {
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

  public int getChildrenCount(int id) {
    return myStubData.get(childrenCountIndex(id));
  }

  public void addStub(@NotNull StubBase<?> stub, @Nullable StubBase<?> parent, @Nullable IElementType type) {
    int stubId = size();
    stub.id = stubId;

    int parentId = parent == null ? -1 : parent.id;
    if (nonDfsOrderDetected(parentId, stubId)) {
      Objects.requireNonNull(myTempState).switchChildrenToTempMap(parentId);
    }

    addStub(stubId, parentId, type == null ? 0 : type.getIndex());
  }

  private boolean nonDfsOrderDetected(int parentId, int childId) {
    return parentId >= 0 && childId != parentId + 1 && getChildrenCount(parentId) == 0;
  }

  public void addStub(int childId, int parentId, short elementTypeIndex) {
    assert myTempState != null;

    myStubData.add(elementTypeIndex); myStubData.add(0); myStubData.add(0);

    if (childId == 0) return;

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

  private int getParentIndex(int childIndex) {
    return ((StubBase<?>)get(childIndex).getParentStub()).id;
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

  @ApiStatus.Internal
  public void prepareForChildren(int parentId, int childrenCount) {
    assert myTempState != null;
    myTempState.prepareForChildren(parentId, childrenCount);
  }

  @ApiStatus.Internal
  public abstract @Nullable StubBase<?> getCachedStub(int index);

  @Unmodifiable
  List<StubBase<?>> getChildrenStubs(int id) {
    int count = getChildrenCount(id);
    if (count == 0) return Collections.emptyList();

    int start = getChildrenStart(id);
    switch (getChildrenStorage(start)) {
      case inPlainList: return subList(id + 1, id + 1 + count);
      case inJoinedList: return idSubList(myJoinedChildrenList, start, count);
      default: return idSubList(tempMap().get(id), 0, count);
    }
  }

  private @Unmodifiable List<StubBase<?>> idSubList(MostlyUShortIntList idList, int start, int count) {
    return new AbstractList<StubBase<?>>() {
      @Override
      public StubBase<?> get(int index) {
        if (index < 0 || index >= count) throw new IndexOutOfBoundsException("index=" + index + ", size=" + count);
        return StubList.this.get(idList.get(start + index));
      }

      @Override
      public int size() {
        return count;
      }
    };
  }

  private IntObjectMap<MostlyUShortIntList> tempMap() {
    assert myTempState != null;
    return Objects.requireNonNull(myTempState.myTempJoinedChildrenMap);
  }

  @Nullable
  <P extends PsiElement, S extends StubElement<P>> S findChildStubByType(int id, @NotNull IElementType elementType) {
    int count = getChildrenCount(id);
    int start = getChildrenStart(id);
    switch (getChildrenStorage(start)) {
      case inPlainList: return findChildStubByType(elementType, IntUnaryOperator.identity(), id + 1, id + 1 + count);
      case inJoinedList: return findChildStubByType(elementType, myJoinedChildrenList, start, start + count);
      default: return findChildStubByType(elementType, Objects.requireNonNull(tempMap()).get(id), 0, count);
    }
  }

  private @Nullable <P extends PsiElement, S extends StubElement<P>> S findChildStubByType(IElementType elementType,
                                                                                           IntUnaryOperator idList,
                                                                                           int start, int end) {
    for (int i = start; i < end; ++i) {
      int id = idList.applyAsInt(i);
      if (elementType.getIndex() == getStubTypeIndex(id)) {
        //noinspection unchecked
        return (S)get(id);
      }
    }
    return null;
  }

  /**
   * Ensures stubs are in DFS order and the optimizes memory layout. Might return an optimized copy of this list,
   * with all stubs re-targeted to that copy.
   */
  @NotNull
  @ApiStatus.Internal
  public StubList finalizeLoadingStage() {
    if (myTempState != null) {
      myTempState = null;
      myJoinedChildrenList.trimToSize();
      myStubData.trimToSize();
    }
    return this;
  }

  @NotNull
  @Unmodifiable
  @ApiStatus.Internal
  public List<StubElement<?>> toPlainList() {
    //noinspection unchecked
    return (List)this;
  }

  @ApiStatus.Internal
  public boolean isChildrenLayoutOptimal() {
    return myTempState == null || myTempState.myTempJoinedChildrenMap == null;
  }

  public boolean areChildrenNonAdjacent(int childId, int parentId) {
    return getParentIndex(childId - 1) != parentId;
  }

  private final class TempState {
    @Nullable IntObjectMap<MostlyUShortIntList> myTempJoinedChildrenMap;

    int myCurrentParent = -1;
    int myExpectedChildrenCount;

    int ensureCapacityForNextChild(int childId, int parentId, int childrenCount) {
      if (myCurrentParent >= 0) {
        if (childrenCount == myExpectedChildrenCount - 1) {
          myCurrentParent = -1;
        }
        else if (parentId != myCurrentParent) {
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
      if (myTempJoinedChildrenMap == null) {
        myTempJoinedChildrenMap = ContainerUtil.createConcurrentIntObjectMap();
      }

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
      assert prev == null: parentId;

      myStubData.set(childrenStartIndex(parentId), IN_TEMP_MAP);
    }

    void prepareForChildren(int parentId, int childrenCount) {
      assert parentId == size() - 1;
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

final class MaterialStubList extends StubList {
  /** The list of all stubs ordered by id. The order is DFS (except maybe temporarily during construction, fixed by {@link #finalizeLoadingStage()} later) */
  private final ArrayList<StubBase<?>> myPlainList;

  MaterialStubList(int initialCapacity) {
    super(initialCapacity);
    myPlainList = new ArrayList<>(initialCapacity);
  }

  @Override
  public void addStub(@NotNull StubBase<?> stub, @Nullable StubBase<?> parent, @Nullable IElementType type) {
    super.addStub(stub, parent, type);
    myPlainList.add(stub);
  }

  @NotNull
  @Override
  @ApiStatus.Internal
  public StubList finalizeLoadingStage() {
    if (!isChildrenLayoutOptimal()) {
      return createOptimizedCopy();
    }

    myPlainList.trimToSize();
    return super.finalizeLoadingStage();
  }

  private @NotNull StubList createOptimizedCopy() {
    MaterialStubList copy = new MaterialStubList(size());
    new Object() {
      void visitStub(StubBase<?> stub, int parentId) {
        int idInCopy = copy.size();
        copy.addStub(idInCopy, parentId, getStubTypeIndex(stub.id));
        copy.myPlainList.add(stub);

        List<StubBase<?>> children = getChildrenStubs(stub.id);
        copy.prepareForChildren(idInCopy, children.size());

        for (StubBase<?> child : children) {
          visitStub(child, idInCopy);
        }
      }
    }.visitStub(get(0), -1);

    assert copy.isChildrenLayoutOptimal();

    for (int i = 0; i < copy.size(); i++) {
      StubBase<?> stub = copy.get(i);
      stub.setStubList(copy);
      stub.id = i;
    }

    return copy.finalizeLoadingStage();
  }

  @Override
  public int size() {
    return myPlainList.size();
  }

  @Override
  public StubBase<?> get(int id) {
    return myPlainList.get(id);
  }

  @Nullable
  @Override
  @ApiStatus.Internal
  public StubBase<?> getCachedStub(int index) {
    return get(index);
  }
}

