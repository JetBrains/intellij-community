// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.openapi.util.Key;
import com.intellij.util.ArrayUtil;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * @author Dmitry Avdeev
 */
public class ObjectStubTree<T extends Stub> {
  private static final Key<ObjectStubTree<?>> STUB_TO_TREE_REFERENCE = Key.create("stub to tree reference");
  protected final ObjectStubBase<?> myRoot;
  private String myDebugInfo;
  private boolean myHasBackReference;
  private final List<T> myPlainList;

  public ObjectStubTree(final @NotNull ObjectStubBase<?> root, boolean withBackReference) {
    myRoot = root;
    myPlainList = enumerateStubs(root);
    if (withBackReference) {
      myRoot.putUserData(STUB_TO_TREE_REFERENCE, this); // This will prevent soft references to stub tree to be collected before all of the stubs are collected.
    }
  }

  public @NotNull Stub getRoot() {
    return myRoot;
  }

  public @NotNull List<T> getPlainList() {
    return myPlainList;
  }

  @NotNull
  List<T> getPlainListFromAllRoots() {
    return getPlainList();
  }

  @ApiStatus.Internal
  public @NotNull Map<StubIndexKey<?, ?>, Map<Object, int[]>> indexStubTree(@Nullable Function<? super StubIndexKey<?, ?>, ? extends Hash.Strategy<Object>> keyHashingStrategyFunction) {
    StubIndexSink sink = new StubIndexSink(keyHashingStrategyFunction);
    final List<T> plainList = getPlainListFromAllRoots();
    for (int i = 0, plainListSize = plainList.size(); i < plainListSize; i++) {
      final Stub stub = plainList.get(i);
      sink.myStubIdx = i;
      StubSerializationUtil.getSerializer(stub).indexStub(stub, sink);
    }

    return sink.getResult();
  }

  protected @NotNull List<T> enumerateStubs(@NotNull Stub root) {
    List<T> result = new ArrayList<>();
    //noinspection unchecked
    enumerateStubsInto(root, (List)result);
    return result;
  }

  private static void enumerateStubsInto(@NotNull Stub root, @NotNull List<? super Stub> result) {
    ((ObjectStubBase)root).id = result.size();
    result.add(root);
    List<? extends Stub> childrenStubs = root.getChildrenStubs();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < childrenStubs.size(); i++) {
      Stub child = childrenStubs.get(i);
      enumerateStubsInto(child, result);
    }
  }

  public void setDebugInfo(@NotNull @NonNls String info) {
    ObjectStubTree<?> ref = getStubTree(myRoot);
    if (ref != null) {
      assert ref == this;
    }
    myHasBackReference = ref != null;
    myDebugInfo = info;
  }

  public static @Nullable ObjectStubTree getStubTree(@NotNull ObjectStubBase root) {
    return root.getUserData(STUB_TO_TREE_REFERENCE);
  }

  public @NonNls String getDebugInfo() {
    return myHasBackReference ? myDebugInfo + "; with backReference" : myDebugInfo;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{myDebugInfo='" + getDebugInfo() + '\'' + ", myRoot=" + myRoot + '}' + hashCode();
  }

  private static final class StubIndexSink implements IndexSink {
    private final Map<StubIndexKey<?, ?>, Map<Object, int[]>> myResult = new HashMap<>();
    private final @Nullable Function<? super StubIndexKey<?, ?>, ? extends Hash.Strategy<Object>> myHashingStrategyFunction;
    private int myStubIdx;

    private StubIndexSink(@Nullable Function<? super StubIndexKey<?, ?>, ? extends Hash.Strategy<Object>> hashingStrategyFunction) {
      myHashingStrategyFunction = hashingStrategyFunction;
    }

    @Override
    public void occurrence(final @NotNull StubIndexKey indexKey, @NotNull Object value) {
      Map<Object, int[]> map = myResult.get(indexKey);
      if (map == null) {
        map = myHashingStrategyFunction == null ? new HashMap<>() : new Object2ObjectOpenCustomHashMap<>(myHashingStrategyFunction.apply((StubIndexKey<?, ?>)indexKey));
        myResult.put(indexKey, map);
      }

      int[] list = map.get(value);
      if (list == null) {
        map.put(value, new int[] {myStubIdx});
      }
      else {
        int lastNonZero = ArrayUtil.lastIndexOfNot(list, 0);
        if (lastNonZero >= 0 && list[lastNonZero] == myStubIdx) {
          // second and subsequent occurrence calls for the same value are no op
          return;
        }
        int lastZero = lastNonZero + 1;

        if (lastZero == list.length) {
          list = ArrayUtil.realloc(list, Math.max(4, list.length << 1));
          map.put(value, list);
        }
        list[lastZero] = myStubIdx;
      }
    }

    public @NotNull Map<StubIndexKey<?, ?>, Map<Object, int[]>> getResult() {
      for (Map<Object, int[]> map : myResult.values()) {
        for (Map.Entry<Object, int[]> entry : map.entrySet()) {
          int[] ints = entry.getValue();
          if (ints.length == 1) {
            continue;
          }

          int firstZero = ArrayUtil.indexOf(ints, 0);
          if (firstZero != -1) {
            map.put(entry.getKey(), ArrayUtil.realloc(ints, firstZero));
          }
        }
      }
      return myResult;
    }
  }
}
