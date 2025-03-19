// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.search;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.ArrayUtil;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.NotNull;

/**
 * Converts the String to the int id and back.
 * It guarantees to return the same id for the equals Strings.
 * Used for compression: instead of keeping many (maybe equal) String references, it is sometimes cheaper to keep int ids in a packed array.
 */
final class IndexedCharsInterner {
  private int[] idToIndex = new int[10];
  private final StringSet set = new StringSet();

  public int toId(@NotNull String name) {
    int index = set.index(name);
    int id;
    if (index == -1) {
      id = set.size();
      set.add(name);
      index = set.index(name);
      assert index != -1;
      if (id >= idToIndex.length) {
        idToIndex = ArrayUtil.realloc(idToIndex, idToIndex.length * 3 / 2);
      }
      idToIndex[id] = index;
      assert ArrayUtil.indexOf(idToIndex, index) == id;
      //noinspection AssertWithSideEffects
      assert toId(name) == id;
    }
    else {
      id = ArrayUtil.indexOf(idToIndex, index);
    }
    assert fromId(id).equals(name);
    return id;
  }

  public @NotNull @NlsSafe String fromId(int id) {
    assert id >= 0 && id < set.size();
    return set.getByIndex(idToIndex[id]);
  }

  private final class StringSet extends ObjectOpenHashSet<String> {
    private StringSet() {
      super(10, 0.9f);
    }

    String getByIndex(int index) {
      Object[] set = this.key;
      return (String)set[index];
    }

    @Override
    protected void rehash(int newCapacity) {
      Object[] oldSet = this.key;
      int oldSize = set.size();

      super.rehash(newCapacity);

      for (int i = 0; i < oldSize - 1; i++) {
        int oldIndex = idToIndex[i];
        String oldString = (String)oldSet[oldIndex];
        int newIndex = index(oldString);
        assert newIndex != -1;
        idToIndex[i] = newIndex;
      }
    }

    int index(String k) {
      Object curr;
      final Object[] key = this.key;
      int pos;
      if ((curr = key[pos = HashCommon.mix(k.hashCode()) & mask]) == null) {
        return -1;
      }
      if (k.equals(curr)) {
        return pos;
      }

      // there's always an unused entry
      while (true) {
        if ((curr = key[pos = pos + 1 & mask]) == null) {
          return -1;
        }
        if (k.equals(curr)) {
          return pos;
        }
      }
    }
  }
}
