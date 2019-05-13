// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.extensions.SortingException;
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
    List<LoadingOrder.Orderable> target = new ArrayList<>();
    target.add(createElement(LoadingOrder.ANY, null, "Any"));
    target.add(createElement(LoadingOrder.FIRST, null, "1"));
    target.add(createElement(LoadingOrder.LAST, null, "2"));
    target.add(createElement(LoadingOrder.ANY, null, "Any"));
    LoadingOrder.Orderable[] array = target.toArray(new LoadingOrder.Orderable[0]);
    assertSequence(array, "1AnyAny2");
  }

  @Test
  public void testStability() {
    List<LoadingOrder.Orderable> target = new ArrayList<>();
    target.add(createElement(LoadingOrder.ANY, null, "1"));
    target.add(createElement(LoadingOrder.ANY, null, "2"));
    target.add(createElement(LoadingOrder.ANY, null, "3"));
    target.add(createElement(LoadingOrder.ANY, null, "4"));
    LoadingOrder.Orderable[] array = target.toArray(new LoadingOrder.Orderable[0]);
    assertSequence(array, "1234");
  }

  @Test
  public void testComplexSorting() {
    List<LoadingOrder.Orderable> target = new ArrayList<>();
    String idOne = "idOne";
    String idTwo = "idTwo";
    target.add(createElement(LoadingOrder.before(idTwo), idOne, "2"));
    target.add(createElement(LoadingOrder.FIRST, null, "0"));
    target.add(createElement(LoadingOrder.LAST, null, "5"));
    target.add(createElement(LoadingOrder.after(idTwo), null, "4"));
    target.add(createElement(LoadingOrder.ANY, idTwo, "3"));
    target.add(createElement(LoadingOrder.before(idOne), null, "1"));
    LoadingOrder.Orderable[] array = target.toArray(new LoadingOrder.Orderable[0]);
    assertSequence(array, "012345");
  }

  @Test
  public void testComplexSorting2() {
    List<LoadingOrder.Orderable> target = new ArrayList<>();
    String idOne = "idOne";
    target.add(createElement(LoadingOrder.before(idOne), null, "2"));
    target.add(createElement(LoadingOrder.after(idOne), null, "4"));
    target.add(createElement(LoadingOrder.FIRST, null, "1"));
    target.add(createElement(LoadingOrder.ANY, idOne, "3"));
    target.add(createElement(LoadingOrder.ANY, null, "5"));
    target.add(createElement(LoadingOrder.LAST, null, "6"));
    LoadingOrder.Orderable[] array = target.toArray(new LoadingOrder.Orderable[0]);
    assertSequence(array, "123456");
  }

  @Test
  public void testComplexSortingBeforeLast() {
    List<LoadingOrder.Orderable> target = new ArrayList<>();
    target.add(createElement(LoadingOrder.LAST, "1", "1"));
    target.add(createElement(LoadingOrder.readOrder("last,before 1"), null, "2"));
    target.add(createElement(LoadingOrder.ANY, null, "3"));
    target.add(createElement(LoadingOrder.before("1'"), null, "4"));
    LoadingOrder.Orderable[] array = target.toArray(new LoadingOrder.Orderable[0]);
    assertSequence(array, "3421");
  }

  private static void assertSequence(LoadingOrder.Orderable[] array, String expected) {
    LoadingOrder.sort(array);
    StringBuffer sequence = buildSequence(array);
    assertEquals(expected, sequence.toString());
  }

  private static StringBuffer buildSequence(LoadingOrder.Orderable[] array) {
    StringBuffer sequence = new StringBuffer();
    for (LoadingOrder.Orderable adapter : array) {
      sequence.append(((MyOrderable)adapter).getID());
    }
    return sequence;
  }

  @Test
  public void testFailingSortingBeforeFirst() {
    List<LoadingOrder.Orderable> target = new ArrayList<>();
    target.add(createElement(LoadingOrder.ANY, null, "good"));
    target.add(createElement(LoadingOrder.FIRST, "first", "bad"));
    target.add(createElement(LoadingOrder.LAST, null, "good"));
    target.add(createElement(LoadingOrder.before("first"), null, "bad"));
    LoadingOrder.Orderable[] array = target.toArray(new LoadingOrder.Orderable[0]);
    checkSortingFailure(array);
  }

  @Test
  public void testFailingSortingFirst() {
    List<LoadingOrder.Orderable> target = new ArrayList<>();
    target.add(createElement(LoadingOrder.ANY, null, "2"));
    target.add(createElement(LoadingOrder.FIRST, "first", "1"));
    target.add(createElement(LoadingOrder.LAST, null, "3"));
    target.add(createElement(LoadingOrder.FIRST, null, "1"));
    LoadingOrder.Orderable[] array = target.toArray(new LoadingOrder.Orderable[0]);
    assertSequence(array, "1123");
  }

  private static void checkSortingFailure(LoadingOrder.Orderable[] array) {
    try {
      LoadingOrder.sort(array);
      fail("Should have failed");
    }
    catch (SortingException e) {
      LoadingOrder.Orderable[] conflictingElements = e.getConflictingElements();
      assertEquals(2, conflictingElements.length);
      assertEquals("bad", ((MyOrderable)conflictingElements[0]).getID());
      assertEquals("bad", ((MyOrderable)conflictingElements[1]).getID());
    }
  }

  @Test
  public void testFailingSortingAfterLast() {
    List<LoadingOrder.Orderable> target = new ArrayList<>();
    target.add(createElement(LoadingOrder.after("last"), null, "bad"));
    target.add(createElement(LoadingOrder.FIRST, null, "good"));
    target.add(createElement(LoadingOrder.LAST, "last", "bad"));
    target.add(createElement(LoadingOrder.ANY, null, "good"));
    LoadingOrder.Orderable[] array = target.toArray(new LoadingOrder.Orderable[0]);
    checkSortingFailure(array);
  }

  @Test
  public void testFailingSortingLast() {
    List<LoadingOrder.Orderable> target = new ArrayList<>();
    target.add(createElement(LoadingOrder.LAST, null, "3"));
    target.add(createElement(LoadingOrder.FIRST, null, "1"));
    target.add(createElement(LoadingOrder.LAST, "last", "3"));
    target.add(createElement(LoadingOrder.ANY, null, "2"));
    LoadingOrder.Orderable[] array = target.toArray(new LoadingOrder.Orderable[0]);
    assertSequence(array, "1233");
  }

  @Test
  public void testFailingSortingComplex() {
    List<LoadingOrder.Orderable> target = new ArrayList<>();
    target.add(createElement(LoadingOrder.after("2"), "1", "bad"));
    target.add(createElement(LoadingOrder.after("3"), "2", "bad"));
    target.add(createElement(LoadingOrder.after("1"), "3", "bad"));
    LoadingOrder.Orderable[] array = target.toArray(new LoadingOrder.Orderable[0]);
    checkSortingFailure(array);
  }

  private static LoadingOrder.Orderable createElement(final LoadingOrder order, final String idString, final String elementId) {
    return new MyOrderable(order, idString, elementId);
  }

  private static class MyOrderable implements LoadingOrder.Orderable {
    private final LoadingOrder myOrder;
    private final String myOrderId;
    private final String myId;

    MyOrderable(LoadingOrder order, String orderId, String id) {
      myOrder = order;
      myOrderId = orderId;
      myId = id;
    }

    @Override
    public String getOrderId() {
      return myOrderId;
    }

    @Override
    public LoadingOrder getOrder() {
      return myOrder;
    }

    public String getID() {
      return myId;
    }
  }
}
