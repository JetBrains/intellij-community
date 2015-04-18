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
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.extensions.SortingException;
import org.jdom.Element;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Alexander Kireyev
 */
public class LoadingOrderTest {
  @Test
  public void testSimpleSorting() {
    List<LoadingOrder.Orderable> target = new ArrayList<LoadingOrder.Orderable>();
    target.add(createElement(LoadingOrder.ANY, null, "Any"));
    target.add(createElement(LoadingOrder.FIRST, null, "1"));
    target.add(createElement(LoadingOrder.LAST, null, "2"));
    target.add(createElement(LoadingOrder.ANY, null, "Any"));
    LoadingOrder.Orderable[] array = target.toArray(new LoadingOrder.Orderable[target.size()]);
    assertSequence(array, "1AnyAny2");
  }

  @Test
  public void testStability() {
    List<LoadingOrder.Orderable> target = new ArrayList<LoadingOrder.Orderable>();
    target.add(createElement(LoadingOrder.ANY, null, "1"));
    target.add(createElement(LoadingOrder.ANY, null, "2"));
    target.add(createElement(LoadingOrder.ANY, null, "3"));
    target.add(createElement(LoadingOrder.ANY, null, "4"));
    LoadingOrder.Orderable[] array = target.toArray(new LoadingOrder.Orderable[target.size()]);
    assertSequence(array, "1234");
  }

  @Test
  public void testComplexSorting() {
    List<LoadingOrder.Orderable> target = new ArrayList<LoadingOrder.Orderable>();
    String idOne = "idOne";
    String idTwo = "idTwo";
    target.add(createElement(LoadingOrder.before(idTwo), idOne, "2"));
    target.add(createElement(LoadingOrder.FIRST, null, "0"));
    target.add(createElement(LoadingOrder.LAST, null, "5"));
    target.add(createElement(LoadingOrder.after(idTwo), null, "4"));
    target.add(createElement(LoadingOrder.ANY, idTwo, "3"));
    target.add(createElement(LoadingOrder.before(idOne), null, "1"));
    LoadingOrder.Orderable[] array = target.toArray(new LoadingOrder.Orderable[target.size()]);
    assertSequence(array, "012345");
  }

  @Test
  public void testComplexSorting2() {
    List<LoadingOrder.Orderable> target = new ArrayList<LoadingOrder.Orderable>();
    String idOne = "idOne";
    target.add(createElement(LoadingOrder.before(idOne), null, "2"));
    target.add(createElement(LoadingOrder.after(idOne), null, "4"));
    target.add(createElement(LoadingOrder.FIRST, null, "1"));
    target.add(createElement(LoadingOrder.ANY, idOne, "3"));
    target.add(createElement(LoadingOrder.ANY, null, "5"));
    target.add(createElement(LoadingOrder.LAST, null, "6"));
    LoadingOrder.Orderable[] array = target.toArray(new LoadingOrder.Orderable[target.size()]);
    assertSequence(array, "123456");
  }

  private static void assertSequence(LoadingOrder.Orderable[] array, String expected) {
    LoadingOrder.sort(array);
    StringBuffer sequence = buildSequence(array);
    assertEquals(expected, sequence.toString());
  }

  private static StringBuffer buildSequence(LoadingOrder.Orderable[] array) {
    StringBuffer sequence = new StringBuffer();
    for (LoadingOrder.Orderable adapter : array) {
      sequence.append(((MyElement)adapter.getDescribingElement()).getID());
    }
    return sequence;
  }

  @Test
  public void testFailingSortingBeforeFirst() {
    List<LoadingOrder.Orderable> target = new ArrayList<LoadingOrder.Orderable>();
    target.add(createElement(LoadingOrder.ANY, null, "good"));
    target.add(createElement(LoadingOrder.FIRST, "first", "bad"));
    target.add(createElement(LoadingOrder.LAST, null, "good"));
    target.add(createElement(LoadingOrder.before("first"), null, "bad"));
    LoadingOrder.Orderable[] array = target.toArray(new LoadingOrder.Orderable[target.size()]);
    checkSortingFailure(array);
  }

  @Test
  public void testFailingSortingFirst() {
    List<LoadingOrder.Orderable> target = new ArrayList<LoadingOrder.Orderable>();
    target.add(createElement(LoadingOrder.ANY, null, "2"));
    target.add(createElement(LoadingOrder.FIRST, "first", "1"));
    target.add(createElement(LoadingOrder.LAST, null, "3"));
    target.add(createElement(LoadingOrder.FIRST, null, "1"));
    LoadingOrder.Orderable[] array = target.toArray(new LoadingOrder.Orderable[target.size()]);
    assertSequence(array, "1123");
  }

  private static void checkSortingFailure(LoadingOrder.Orderable[] array) {
    try {
      LoadingOrder.sort(array);
      fail("Should have failed");
    }
    catch (SortingException e) {
      Element[] conflictingElements = e.getConflictingElements();
      assertEquals(2, conflictingElements.length);
      assertEquals("bad", ((MyElement)conflictingElements[0]).getID());
      assertEquals("bad", ((MyElement)conflictingElements[1]).getID());
    }
  }

  @Test
  public void testFailingSortingAfterLast() {
    List<LoadingOrder.Orderable> target = new ArrayList<LoadingOrder.Orderable>();
    target.add(createElement(LoadingOrder.after("last"), null, "bad"));
    target.add(createElement(LoadingOrder.FIRST, null, "good"));
    target.add(createElement(LoadingOrder.LAST, "last", "bad"));
    target.add(createElement(LoadingOrder.ANY, null, "good"));
    LoadingOrder.Orderable[] array = target.toArray(new LoadingOrder.Orderable[target.size()]);
    checkSortingFailure(array);
  }

  @Test
  public void testFailingSortingLast() {
    List<LoadingOrder.Orderable> target = new ArrayList<LoadingOrder.Orderable>();
    target.add(createElement(LoadingOrder.LAST, null, "3"));
    target.add(createElement(LoadingOrder.FIRST, null, "1"));
    target.add(createElement(LoadingOrder.LAST, "last", "3"));
    target.add(createElement(LoadingOrder.ANY, null, "2"));
    LoadingOrder.Orderable[] array = target.toArray(new LoadingOrder.Orderable[target.size()]);
    assertSequence(array, "1233");
  }

  @Test
  public void testFailingSortingComplex() {
    List<LoadingOrder.Orderable> target = new ArrayList<LoadingOrder.Orderable>();
    target.add(createElement(LoadingOrder.after("2"), "1", "bad"));
    target.add(createElement(LoadingOrder.after("3"), "2", "bad"));
    target.add(createElement(LoadingOrder.after("1"), "3", "bad"));
    LoadingOrder.Orderable[] array = target.toArray(new LoadingOrder.Orderable[target.size()]);
    checkSortingFailure(array);
  }

  private static LoadingOrder.Orderable createElement(final LoadingOrder order, final String idString, final String elementId) {
    return new LoadingOrder.Orderable() {
      @Override
      public String getOrderId() {
        return idString;
      }

      @Override
      public LoadingOrder getOrder() {
        return order;
      }

      @Override
      public Element getDescribingElement() {
        return new MyElement(elementId);
      }
    };
  }

  private static class MyElement extends Element {
    private final String myID;

    public MyElement(String ID) {
      myID = ID;
    }

    public String getID() {
      return myID;
    }
  }
}
