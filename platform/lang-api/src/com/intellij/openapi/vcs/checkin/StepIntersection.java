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
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;

public class StepIntersection<Data, Area> {
  private final Convertor<Data,TextRange> myDataConvertor;
  private final Convertor<Area,TextRange> myAreasConvertor;
  private TextRange myDataRange;
  private TextRange myAreaRange;
  private Data myCurData;
  private Iterator<Data> myDataIterator;
  private int myAreaIndex;
  private Area myCurArea;
  private final List<Area> myAreas;
  private final HackSearch<Data,Area,TextRange> myHackSearch;

  public static <Data, Area> void processIntersections(@NotNull List<Data> elements1,
                                                       @NotNull List<Area> elements2,
                                                       @NotNull Convertor<Data, TextRange> convertor1,
                                                       @NotNull Convertor<Area, TextRange> convertor2,
                                                       @NotNull PairConsumer<Data, Area> intersectionConsumer) {
    new StepIntersection<>(convertor1, convertor2, elements2).process(elements1, intersectionConsumer);
  }

  private StepIntersection(Convertor<Data, TextRange> dataConvertor,
                           Convertor<Area, TextRange> areasConvertor,
                           final List<Area> areas) {
    myAreas = areas;
    myAreaIndex = 0;
    myDataConvertor = dataConvertor;
    myAreasConvertor = areasConvertor;
    myHackSearch = new HackSearch<>(myDataConvertor, myAreasConvertor,
                                    (o1, o2) -> o1.intersects(o2) ? 0 : o1.getStartOffset() < o2.getStartOffset() ? -1 : 1);
  }

  private void process(final Iterable<Data> data, final PairConsumer<Data, Area> consumer) {
    myDataIterator = data.iterator();

    if (! myDataIterator.hasNext() || noMoreAreas()) return;
    dataStep();
    initArea();
    while (! noMoreAreas()) {
      final boolean intersects = myAreaRange.intersects(myDataRange);
      if (intersects) {
        consumer.consume(myCurData, myCurArea);
      }
      // take next
      if (! myDataIterator.hasNext() && noMoreAreas()) break;
      if (! myDataIterator.hasNext()) {
        areaStep();
        continue;
      }
      if (noMoreAreas()) {
        dataStep();
        continue;
      }
      if (myDataRange.getEndOffset() < myAreaRange.getEndOffset()) {
        dataStep();
      } else {
        areaStep();
      }
    }
  }

  private boolean noMoreAreas() {
    return (myAreaIndex >= myAreas.size());
  }

  private void initArea() {
    myAreaIndex = 0;
    myCurArea = myAreas.get(myAreaIndex);
    myAreaRange = myAreasConvertor.convert(myCurArea);
  }

  private void areaStep() {
    // a hack here
    final int idx = myHackSearch.search(myAreas.subList(myAreaIndex + 1, myAreas.size()), myCurData);
    myAreaIndex = myAreaIndex + 1 + idx;
    if (myAreaIndex >= myAreas.size()) {
      return;
    }
    /*assert myAreaRange == null || myAreaRange.getEndOffset() < myAreasConvertor.convert(myAreas.get(myAreaIndex)).getStartOffset() :
      "Area ranges intersect: first: " + myAreaRange + ", second: " + myAreasConvertor.convert(myAreas.get(myAreaIndex)) + ", text: '" +
      myDebugDocumentTextGetter.get() + "'";*/
    myCurArea = myAreas.get(myAreaIndex);
    myAreaRange = myAreasConvertor.convert(myCurArea);
  }

  private void dataStep() {
    myCurData = myDataIterator.next();
    /*assert myDataRange == null || myDataRange.getEndOffset() < myDataConvertor.convert(myCurData).getStartOffset() :
      "Data ranges intersect: first: " + myDataRange + ", second: " + myDataConvertor.convert(myCurData) + ", text: '" +
      myDebugDocumentTextGetter.get() + "'";*/
    myDataRange = myDataConvertor.convert(myCurData);
  }
}
