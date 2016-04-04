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
package com.intellij.psi.stubsHierarchy.impl;

import com.intellij.util.ArrayUtil;
import gnu.trove.TObjectHashingStrategy;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class NamesEnumerator {
  final static int NO_NAME = 0;

  private TObjectIntHashMap<byte[]> myAsciiMap = new TObjectIntHashMap<byte[]>(new TObjectHashingStrategy<byte[]>() {
    @Override
    public int computeHashCode(byte[] object) {
      return Arrays.hashCode(object);
    }

    @Override
    public boolean equals(byte[] o1, byte[] o2) {
      return Arrays.equals(o1, o2);
    }
  });

  private TObjectIntHashMap<String> myNonAsciiMap = new TObjectIntHashMap<String>();

  TObjectIntHashMap<int[]> fullNameMap = new TObjectIntHashMap<int[]>(new TObjectHashingStrategy<int[]>() {
    @Override
    public int computeHashCode(int[] object) {
      return Arrays.hashCode(object);
    }

    @Override
    public boolean equals(int[] o1, int[] o2) {
      return Arrays.equals(o1, o2);
    }
  });

  private QualifiedName[] myQualifiedNames = new QualifiedName[0x8000];

  QualifiedName qualifiedName(int id) {
    return myQualifiedNames[id];
  }

  public int getSimpleName(String s, boolean create) {
    byte[] bytes = convertToBytesIfAsciiString(s);
    if (bytes != null) {
      int id = myAsciiMap.get(bytes);
      if (id == 0 && create) {
        id = myAsciiMap.size() + myNonAsciiMap.size() + 1;
        myAsciiMap.put(bytes, id);
      }
      return id;
    }
    else {
      s = new String(s);
      int id = myNonAsciiMap.get(s);
      if (id == 0 && create) {
        id = myAsciiMap.size() + myNonAsciiMap.size() + 1;
        myNonAsciiMap.put(s, id);
      }
      return id;
    }
  }

  public QualifiedName getFullName(int[] ids, boolean create) {
    int id = fullNameMap.get(ids);
    if (id == 0 && create) {
      id = fullNameMap.size() + 1;
      fullNameMap.put(ids, id);
      ensureFullCapacity(id);
      myQualifiedNames[id] = new QualifiedName(id, ids);
    }
    return myQualifiedNames[id];
  }


  private void ensureFullCapacity(int maxIndex) {
    if (maxIndex >= myQualifiedNames.length) {
      int newLength = calculateNewLength(myQualifiedNames.length, maxIndex);
      QualifiedName[] names1 = new QualifiedName[newLength];
      System.arraycopy(myQualifiedNames, 0, names1, 0, myQualifiedNames.length);
      myQualifiedNames = names1;
    }
  }

  private static int calculateNewLength(int currentLength, int maxIndex) {
    while (currentLength < maxIndex + 1)
      currentLength *= 2;
    return currentLength;
  }

  @Nullable
  public static byte[] convertToBytesIfAsciiString(CharSequence name) {
    int length = name.length();
    if (length == 0) return ArrayUtil.EMPTY_BYTE_ARRAY;

    byte[] bytes = new byte[length];
    for (int i = 0; i < length; i++) {
      char c = name.charAt(i);
      if (c >= 128) {
        return null;
      }
      bytes[i] = (byte)c;
    }
    return bytes;
  }

}
