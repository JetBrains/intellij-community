// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.extensions.SortingException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class LoadingOrderTest {
  @Test
  public void testSimpleSorting() {
    assertSequence(
      "1 Any Any 2",
      ContainerUtil.newArrayList(
      createElement(LoadingOrder.ANY, null, "Any"),
      createElement(LoadingOrder.FIRST, null, "1"),
      createElement(LoadingOrder.LAST, null, "2"),
      createElement(LoadingOrder.ANY, null, "Any")
      )
    );
  }

  @Test
  public void testStability() {
    assertSequence(
      "1 2 3 4",
      ContainerUtil.newArrayList(
      createElement(LoadingOrder.ANY, null, "1"),
      createElement(LoadingOrder.ANY, null, "2"),
      createElement(LoadingOrder.ANY, null, "3"),
      createElement(LoadingOrder.ANY, null, "4")
      )
    );
  }

  @Test
  public void testComplexSorting() {
    String idOne = "idOne";
    String idTwo = "idTwo";

    assertSequence(
      "0 1 2 3 4 5",
      ContainerUtil.newArrayList(
      createElement(LoadingOrder.before(idTwo), idOne, "2"),
      createElement(LoadingOrder.FIRST, null, "0"),
      createElement(LoadingOrder.LAST, null, "5"),
      createElement(LoadingOrder.after(idTwo), null, "4"),
      createElement(LoadingOrder.ANY, idTwo, "3"),
      createElement(LoadingOrder.before(idOne), null, "1")
      )
    );
  }

  @Test
  public void testComplexSorting2() {
    String idOne = "idOne";

    assertSequence(
      "1 2 3 4 5 6",
      ContainerUtil.newArrayList(
      createElement(LoadingOrder.before(idOne), null, "2"),
      createElement(LoadingOrder.after(idOne), null, "4"),
      createElement(LoadingOrder.FIRST, null, "1"),
      createElement(LoadingOrder.ANY, idOne, "3"),
      createElement(LoadingOrder.ANY, null, "5"),
      createElement(LoadingOrder.LAST, null, "6")
      )
    );
  }

  @Test
  public void testComplexSortingBeforeLast() {
    assertSequence(
      "3 4 2 1",
      ContainerUtil.newArrayList(
      createElement(LoadingOrder.LAST, "1", "1"),
      createElement(LoadingOrder.readOrder("last,before 1"), null, "2"),
      createElement(LoadingOrder.ANY, null, "3"),
      createElement(LoadingOrder.before("1'"), null, "4")
      )
    );
  }

  @Test
  public void testFailingSortingBeforeFirst() {
    checkSortingFailure(
      ContainerUtil.newArrayList(
      createElement(LoadingOrder.ANY, null, "good"),
      createElement(LoadingOrder.FIRST, "first", "bad"),
      createElement(LoadingOrder.LAST, null, "good"),
      createElement(LoadingOrder.before("first"), null, "bad")
      )
    );
  }

  // XXX: This test doesn't actually fail, despite its name.
  @Test
  public void testFailingSortingFirst() {
    assertSequence(
      "1 1 2 3",
      ContainerUtil.newArrayList(
      createElement(LoadingOrder.ANY, null, "2"),
      createElement(LoadingOrder.FIRST, "first", "1"),
      createElement(LoadingOrder.LAST, null, "3"),
      createElement(LoadingOrder.FIRST, null, "1")
      )
    );
  }
  @Test
  public void testFailingSortingAfterLast() {
    checkSortingFailure(
      ContainerUtil.newArrayList(
      createElement(LoadingOrder.after("last"), null, "bad"),
      createElement(LoadingOrder.FIRST, null, "good"),
      createElement(LoadingOrder.LAST, "last", "bad"),
      createElement(LoadingOrder.ANY, null, "good")
      )
    );
  }

  @Test
  public void testFailingSortingLast() {
    assertSequence(
      "1 2 3 3",
      ContainerUtil.newArrayList(
      createElement(LoadingOrder.LAST, null, "3"),
      createElement(LoadingOrder.FIRST, null, "1"),
      createElement(LoadingOrder.LAST, "last", "3"),
      createElement(LoadingOrder.ANY, null, "2")
      )
    );
  }

  @Test
  public void testFailingSortingComplex() {
    checkSortingFailure(ContainerUtil.newArrayList(
                          createElement(LoadingOrder.after("2"), "1", "bad"),
                          createElement(LoadingOrder.after("3"), "2", "bad"),
                          createElement(LoadingOrder.after("1"), "3", "bad")
                        )
    );
  }

  /**
   * Assert that after sorting the given elements, their IDs form the expected string.
   */
  private static void assertSequence(String expected, @NotNull List<LoadingOrder.Orderable> list) {
    LoadingOrder.Companion.sort(list);
    String sequence = list.stream().map(o -> ((MyOrderable)o).getName()).collect(Collectors.joining(" "));
    assertEquals(expected, sequence);
  }

  /**
   * Ensure that the given elements cannot be sorted, due to conflicting constraints.
   * All elements that are involved in the conflicts must have the name "bad".
   */
  private static void checkSortingFailure(@NotNull List<LoadingOrder.Orderable> list) {
    try {
      LoadingOrder.Companion.sort(list);
      fail("Should have failed");
    }
    catch (SortingException e) {
      LoadingOrder.Orderable[] conflictingElements = e.getConflictingElements();
      assertEquals(2, conflictingElements.length);
      assertEquals("bad", ((MyOrderable)conflictingElements[0]).getName());
      assertEquals("bad", ((MyOrderable)conflictingElements[1]).getName());
    }
  }

  /**
   * @param orderId the ID that is used in "before" and "after" constraints
   * @param name    the test-only name, only used for tracking the elements
   */
  private static LoadingOrder.Orderable createElement(LoadingOrder order, String orderId, String name) {
    return new MyOrderable(order, orderId, name);
  }

  private static final class MyOrderable implements LoadingOrder.Orderable {
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
    public @NotNull LoadingOrder getOrder() {
      return myOrder;
    }

    public String getName() {
      return myName;
    }
  }
}
