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

import gnu.trove.TObjectHashingStrategy;
import gnu.trove.TObjectIntHashMap;

import java.util.Arrays;

public class NamesEnumerator {
  final static int NO_NAME = 0;

  private final TObjectIntHashMap<int[]> myFullNameMap = new TObjectIntHashMap<int[]>(new TObjectHashingStrategy<int[]>() {
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

  public QualifiedName getFullName(int[] ids, boolean create) {
    int id = myFullNameMap.get(ids);
    if (id == 0 && create) {
      id = myFullNameMap.size() + 1;
      myFullNameMap.put(ids, id);
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

}
