/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.openapi.util.Key;
import gnu.trove.THashMap;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StubTree {
  private static final Key<StubTree> STUB_TO_TREE_REFERENCE = Key.create("stub to tree reference");
  private final PsiFileStub myRoot;
  private final List<StubElement<?>> myPlainList = new ArrayList<StubElement<?>>();

  public StubTree(@NotNull final PsiFileStub root) {
    this(root, true);
  }

  public StubTree(@NotNull final PsiFileStub root, final boolean withBackReference) {
    myRoot = root;
    enumerateStubs(root, myPlainList);
    if (withBackReference) {
      myRoot.putUserData(STUB_TO_TREE_REFERENCE, this); // This will prevent soft references to stub tree to be collected before all of the stubs are collected.
    }
  }

  private static void enumerateStubs(final StubElement<?> root, final List<StubElement<?>> result) {
    ((StubBase)root).id = result.size();
    result.add(root);
    for (StubElement child : root.getChildrenStubs()) {
      enumerateStubs(child, result);
    }
  }

  @NotNull
  public PsiFileStub getRoot() {
    return myRoot;
  }

  public List<StubElement<?>> getPlainList() {
    return myPlainList;
  }

  @NotNull
  public Map<StubIndexKey, Map<Object, TIntArrayList>> indexStubTree() {
    final Map<StubIndexKey, Map<Object, TIntArrayList>> result = new HashMap<StubIndexKey, Map<Object, TIntArrayList>>();

    SerializationManager serializationManager = SerializationManager.getInstance();
    StubIndexSink sink = new StubIndexSink(result);

    for (int i = 0, plainListSize = myPlainList.size(); i < plainListSize; i++) {
      final StubElement<?> stub = myPlainList.get(i);
      sink.myStubIdx = i;
      serializationManager.getSerializer(stub).indexStub(stub, sink);
    }

    return result;
  }

  private static class StubIndexSink implements IndexSink {
    private final Map<StubIndexKey, Map<Object, TIntArrayList>> myResult;
    private int myStubIdx;

    public StubIndexSink(Map<StubIndexKey, Map<Object, TIntArrayList>> result) {
      myResult = result;
    }

    @Override
    public void occurrence(@NotNull final StubIndexKey indexKey, @NotNull final Object value) {
      Map<Object, TIntArrayList> map = myResult.get(indexKey);
      if (map == null) {
        map = new THashMap<Object, TIntArrayList>();
        myResult.put(indexKey, map);
      }

      TIntArrayList list = map.get(value);
      if (list == null) {
        list = new TIntArrayList();
        map.put(value, list);
      }
      list.add(myStubIdx);
    }
  }
}
