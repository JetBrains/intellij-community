// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.extensions.SortingException;
import org.junit.Test;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Alexander Kireyev
 */
public class LoadingOrderTest {

  @Test
  public void testSimpleSorting() {
    assertSequence(
      "1 Any Any 2",

      createElement(LoadingOrder.ANY, null, "Any"),
      createElement(LoadingOrder.FIRST, null, "1"),
      createElement(LoadingOrder.LAST, null, "2"),
      createElement(LoadingOrder.ANY, null, "Any")
    );
  }

  @Test
  public void testStability() {
    assertSequence(
      "1 2 3 4",

      createElement(LoadingOrder.ANY, null, "1"),
      createElement(LoadingOrder.ANY, null, "2"),
      createElement(LoadingOrder.ANY, null, "3"),
      createElement(LoadingOrder.ANY, null, "4")
    );
  }

  @Test
  public void testComplexSorting() {
    String idOne = "idOne";
    String idTwo = "idTwo";

    assertSequence(
      "0 1 2 3 4 5",

      createElement(LoadingOrder.before(idTwo), idOne, "2"),
      createElement(LoadingOrder.FIRST, null, "0"),
      createElement(LoadingOrder.LAST, null, "5"),
      createElement(LoadingOrder.after(idTwo), null, "4"),
      createElement(LoadingOrder.ANY, idTwo, "3"),
      createElement(LoadingOrder.before(idOne), null, "1")
    );
  }

  @Test
  public void testComplexSorting2() {
    String idOne = "idOne";

    assertSequence(
      "1 2 3 4 5 6",

      createElement(LoadingOrder.before(idOne), null, "2"),
      createElement(LoadingOrder.after(idOne), null, "4"),
      createElement(LoadingOrder.FIRST, null, "1"),
      createElement(LoadingOrder.ANY, idOne, "3"),
      createElement(LoadingOrder.ANY, null, "5"),
      createElement(LoadingOrder.LAST, null, "6")
    );
  }

  @Test
  public void testComplexSortingBeforeLast() {
    assertSequence(
      "3 4 2 1",

      createElement(LoadingOrder.LAST, "1", "1"),
      createElement(LoadingOrder.readOrder("last,before 1"), null, "2"),
      createElement(LoadingOrder.ANY, null, "3"),
      createElement(LoadingOrder.before("1'"), null, "4")
    );
  }

  /**
   * Asserts that after sorting the given elements, their IDs form the expected string.
   */
  private static void assertSequence(String expected, LoadingOrder.Orderable... array) {
    LoadingOrder.sort(array);
    String sequence = Arrays.stream(array).map(o -> ((MyOrderable)o).getName()).collect(Collectors.joining(" "));
    assertEquals(expected, sequence);
  }

  @Test
  public void testFailingSortingBeforeFirst() {
    checkSortingFailure(
      createElement(LoadingOrder.ANY, null, "good"),
      createElement(LoadingOrder.FIRST, "first", "bad"),
      createElement(LoadingOrder.LAST, null, "good"),
      createElement(LoadingOrder.before("first"), null, "bad")
    );
  }

  // XXX: This test doesn't actually fail, despite its name.
  @Test
  public void testFailingSortingFirst() {
    assertSequence(
      "1 1 2 3",

      createElement(LoadingOrder.ANY, null, "2"),
      createElement(LoadingOrder.FIRST, "first", "1"),
      createElement(LoadingOrder.LAST, null, "3"),
      createElement(LoadingOrder.FIRST, null, "1")
    );
  }

  private static void checkSortingFailure(LoadingOrder.Orderable... array) {
    try {
      LoadingOrder.sort(array);
      fail("Should have failed");
    }
    catch (SortingException e) {
      LoadingOrder.Orderable[] conflictingElements = e.getConflictingElements();
      assertEquals(2, conflictingElements.length);
      assertEquals("bad", ((MyOrderable)conflictingElements[0]).getName());
      assertEquals("bad", ((MyOrderable)conflictingElements[1]).getName());
    }
  }

  @Test
  public void testFailingSortingAfterLast() {
    checkSortingFailure(
      createElement(LoadingOrder.after("last"), null, "bad"),
      createElement(LoadingOrder.FIRST, null, "good"),
      createElement(LoadingOrder.LAST, "last", "bad"),
      createElement(LoadingOrder.ANY, null, "good")
    );
  }

  @Test
  public void testFailingSortingLast() {
    assertSequence(
      "1 2 3 3",

      createElement(LoadingOrder.LAST, null, "3"),
      createElement(LoadingOrder.FIRST, null, "1"),
      createElement(LoadingOrder.LAST, "last", "3"),
      createElement(LoadingOrder.ANY, null, "2")
    );
  }

  @Test
  public void testFailingSortingComplex() {
    checkSortingFailure(
      createElement(LoadingOrder.after("2"), "1", "bad"),
      createElement(LoadingOrder.after("3"), "2", "bad"),
      createElement(LoadingOrder.after("1"), "3", "bad")
    );
  }

  /**
   * @param orderId the ID that is used in "before" and "after" constraints
   * @param name    the test-only name, only used for tracking the elements
   */
  private static LoadingOrder.Orderable createElement(LoadingOrder order, String orderId, String name) {
    return new MyOrderable(order, orderId, name);
  }

  private static class MyOrderable implements LoadingOrder.Orderable {
    private final LoadingOrder myOrder;
    private final String myOrderId;
    private final String myName;

    MyOrderable(LoadingOrder order, String orderId, String name) {
      myOrder = order;
      myOrderId = orderId;
      myName = name;
    }

    @Override
    public String getOrderId() {
      return myOrderId;
    }

    @Override
    public LoadingOrder getOrder() {
      return myOrder;
    }

    public String getName() {
      return myName;
    }
  }
}
