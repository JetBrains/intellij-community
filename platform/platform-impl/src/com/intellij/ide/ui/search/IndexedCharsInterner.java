// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.search;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.FastUtilHashingStrategies;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import org.jetbrains.annotations.NotNull;

/**
 * Converts the String to the int id and back.
 * It guarantees to return the same id for the equals Strings.
 * Used for compression: instead of keeping many (maybe equal) String references it sometimes cheaper to keep int ids in a packed array.
 */
final class IndexedCharsInterner {
  private int[] idToIndex = new int[10];

  private final MySet mySet = new MySet();

  public int toId(@NotNull String name) {
    int index = mySet.index(name);
    int id;
    if (index == -1) {
      id = mySet.size();
      mySet.add(name);
      index = mySet.index(name);
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
    assert StringUtilRt.equal(fromId(id), name, true);
    return id;
  }

  public @NotNull @NlsSafe CharSequence fromId(int id) {
    assert id >= 0 && id < mySet.size();
    return mySet.getByIndex(idToIndex[id]);
  }

  private final class MySet extends ObjectOpenCustomHashSet<CharSequence> {
    MySet() {
      super(10, 0.9f, FastUtilHashingStrategies.getCharSequenceStrategy(true));
    }

    CharSequence getByIndex(int index) {
      Object[] set = this.key;
      return (CharSequence)set[index];
    }

    @Override
    protected void rehash(int newCapacity) {
      Object[] oldSet = this.key;
      int oldSize = mySet.size();

      super.rehash(newCapacity);

      for (int i = 0; i < oldSize - 1; i++) {
        int oldIndex = idToIndex[i];
        CharSequence oldString = (CharSequence)oldSet[oldIndex];
        int newIndex = index(oldString);
        assert newIndex != -1;
        idToIndex[i] = newIndex;
      }
    }

    int index(CharSequence k) {
      Object curr;
      final Object[] key = this.key;
      int pos;
      if ((curr = key[pos = HashCommon.mix(strategy.hashCode(k)) & mask]) == null) {
        return -1;
      }
      if (strategy.equals(k, (CharSequence)curr)) {
        return pos;
      }

      // There's always an unused entry.
      while (true) {
        if ((curr = key[pos = pos + 1 & mask]) == null) {
          return -1;
        }
        if (strategy.equals(k, (CharSequence)curr)) {
          return pos;
        }
      }
    }
  }
}
