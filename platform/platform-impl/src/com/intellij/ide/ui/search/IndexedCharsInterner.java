/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.ui.search;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.OpenTHashSet;
import com.intellij.util.text.ByteArrayCharSequence;
import com.intellij.util.text.CharSequenceHashingStrategy;
import org.jetbrains.annotations.NotNull;

/**
 * Converts the String to the int id and back.
 * It guarantees to return the same id for the equals Strings.
 * Used for compression: instead of keeping many (maybe equal) String references it sometimes cheaper to keep int ids in a packed array.
 */
class IndexedCharsInterner {
  private int[] idToIndex = new int[10];

  private final OpenTHashSet<CharSequence> mySet = new OpenTHashSet<CharSequence>(10, 0.9f, CharSequenceHashingStrategy.CASE_SENSITIVE) {
    @Override
    protected void rehash(int newCapacity) {
      Object[] oldSet = _set;
      int oldSize = mySet.size();
      super.rehash(newCapacity);
      for (int i = 0; i < oldSize-1; i++) {
        int oldIndex = idToIndex[i];
        CharSequence oldString = (CharSequence)oldSet[oldIndex];
        int newIndex = index(oldString);
        assert newIndex != -1;
        idToIndex[i] = newIndex;
      }
    }
  };

  public int toId(@NotNull String name) {
    CharSequence seq = ByteArrayCharSequence.convertToBytesIfAsciiString(name);
    int index = mySet.index(seq);
    int id;
    if (index == -1) {
      id = mySet.size();
      mySet.add(seq);
      index = mySet.index(seq);
      assert index != -1;
      if (id >= idToIndex.length) {
        idToIndex = ArrayUtil.realloc(idToIndex, idToIndex.length * 3 / 2);
      }
      idToIndex[id] = index;
      assert ArrayUtil.indexOf(idToIndex, index) == id;
      assert toId(name) == id;
    }
    else {
      id = ArrayUtil.indexOf(idToIndex, index);
    }
    assert StringUtil.equals(fromId(id), name);
    return id;
  }

  @NotNull
  public CharSequence fromId(int id) {
    assert id >=0 && id < mySet.size();
    return mySet.get(idToIndex[id]);
  }
}
