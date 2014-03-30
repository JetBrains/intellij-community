/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.Convertor;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author irengrig
 *         Date: 2/21/11
 *         Time: 12:05 PM
 * if T and S are compared by convertion to Z, we can find where some t should be placed in s list
 *
 */
public class HackSearch<T,S,Z> {
  private final Convertor<T,Z> myTZConvertor;
  private final Convertor<S,Z> mySZConvertor;
  private final Comparator<Z> myZComparator;
  private S myFake;
  private Z myFakeConverted;
  private final Comparator<S> myComparator;

  public HackSearch(Convertor<T, Z> TZConvertor, Convertor<S, Z> SZConvertor, Comparator<Z> zComparator) {
    myTZConvertor = TZConvertor;
    mySZConvertor = SZConvertor;
    myZComparator = zComparator;
    myComparator = new Comparator<S>() {
    @Override
    public int compare(S o1, S o2) {
      Z z1 = mySZConvertor.convert(o1);
      Z z2 = mySZConvertor.convert(o2);
      if (o1 == myFake) {
        z1 = myFakeConverted;
      } else if (o2 == myFake) {
        z2 = myFakeConverted;
      }
      return myZComparator.compare(z1, z2);
    }
  };
  }

  public int search(final List<S> list, final T item) {
    if (list.isEmpty()) return 0;
    myFake = list.get(0);
    myFakeConverted = myTZConvertor.convert(item);
    if (myZComparator.compare(mySZConvertor.convert(myFake), myTZConvertor.convert(item)) >= 0) {
      return 0;
    }

    final int idx = Collections.binarySearch(list.subList(1, list.size()), myFake, myComparator);
    if (idx >= 0) {
      return 1 + idx;
    } else {
      return - idx;
    }
  }
}
