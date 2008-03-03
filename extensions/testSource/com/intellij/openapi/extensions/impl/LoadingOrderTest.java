/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.extensions.SortingException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jmock.cglib.Mock;
import org.jmock.cglib.MockObjectTestCase;
import org.jmock.core.stub.ReturnStub;

import java.util.ArrayList;

/**
 * @author Alexander Kireyev
 */
public class LoadingOrderTest extends MockObjectTestCase {
  public void testSimpleSorting() {
    ArrayList<LoadingOrder.Orderable> target = new ArrayList<LoadingOrder.Orderable>();
    target.add(createElement(LoadingOrder.ANY, null, "any"));
    target.add(createElement(LoadingOrder.FIRST, null, "1"));
    target.add(createElement(LoadingOrder.LAST, null, "2"));
    target.add(createElement(LoadingOrder.ANY, null, "any"));
    LoadingOrder.Orderable[] array = target.toArray(new LoadingOrder.Orderable[target.size()]);
    assertSequence(array, "1anyany2");
  }

  public void testComplexSorting() {
    ArrayList<LoadingOrder.Orderable> target = new ArrayList<LoadingOrder.Orderable>();
    @NonNls String idOne = "idone";
    @NonNls String idTwo = "idTwo";
    target.add(createElement(LoadingOrder.before(idTwo), idOne, "2"));
    target.add(createElement(LoadingOrder.FIRST, null, "0"));
    target.add(createElement(LoadingOrder.LAST, null, "5"));
    target.add(createElement(LoadingOrder.after(idTwo), null, "4"));
    target.add(createElement(LoadingOrder.ANY, idTwo, "3"));
    target.add(createElement(LoadingOrder.before(idOne), null, "1"));
    LoadingOrder.Orderable[] array = target.toArray(new LoadingOrder.Orderable[target.size()]);
    assertSequence(array, "012345");
  }

  public void testComplexSorting2() {
    ArrayList<LoadingOrder.Orderable> target = new ArrayList<LoadingOrder.Orderable>();
    @NonNls String idOne = "idone";
    target.add(createElement(LoadingOrder.before(idOne), null, "3"));
    target.add(createElement(LoadingOrder.after(idOne), null, "5"));
    target.add(createElement(LoadingOrder.FIRST, null, "1"));
    target.add(createElement(LoadingOrder.ANY, idOne, "4"));
    target.add(createElement(LoadingOrder.ANY, null, "2"));
    target.add(createElement(LoadingOrder.LAST, null, "6"));
    LoadingOrder.Orderable[] array = target.toArray(new LoadingOrder.Orderable[target.size()]);
    assertSequence(array, "123456");
  }

  private static void assertSequence(LoadingOrder.Orderable[] array, @NonNls String expected) {
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

  public void testFailingSortingBeforeFirst() {
    ArrayList<LoadingOrder.Orderable> target = new ArrayList<LoadingOrder.Orderable>();
    target.add(createElement(LoadingOrder.ANY, null, "good"));
    target.add(createElement(LoadingOrder.FIRST, "first", "bad"));
    target.add(createElement(LoadingOrder.LAST, null, "good"));
    target.add(createElement(LoadingOrder.before("first"), null, "bad"));
    LoadingOrder.Orderable[] array = target.toArray(new LoadingOrder.Orderable[target.size()]);
    checkSortingFailure(array);
  }

  public void testFailingSortingFirst() {
    ArrayList<LoadingOrder.Orderable> target = new ArrayList<LoadingOrder.Orderable>();
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

  public void testFailingSortingAfterLast() {
    ArrayList<LoadingOrder.Orderable> target = new ArrayList<LoadingOrder.Orderable>();
    target.add(createElement(LoadingOrder.after("last"), null, "bad"));
    target.add(createElement(LoadingOrder.FIRST, null, "good"));
    target.add(createElement(LoadingOrder.LAST, "last", "bad"));
    target.add(createElement(LoadingOrder.ANY, null, "good"));
    LoadingOrder.Orderable[] array = target.toArray(new LoadingOrder.Orderable[target.size()]);
    checkSortingFailure(array);
  }

  public void testFailingSortingLast() {
    ArrayList<LoadingOrder.Orderable> target = new ArrayList<LoadingOrder.Orderable>();
    target.add(createElement(LoadingOrder.LAST, null, "3"));
    target.add(createElement(LoadingOrder.FIRST, null, "1"));
    target.add(createElement(LoadingOrder.LAST, "last", "3"));
    target.add(createElement(LoadingOrder.ANY, null, "2"));
    LoadingOrder.Orderable[] array = target.toArray(new LoadingOrder.Orderable[target.size()]);
    assertSequence(array, "1233");
  }

  public void testFailingSortingComplex() {
    ArrayList<LoadingOrder.Orderable> target = new ArrayList<LoadingOrder.Orderable>();
    target.add(createElement(LoadingOrder.after("2"), "1", "bad"));
    target.add(createElement(LoadingOrder.after("3"), "2", "bad"));
    target.add(createElement(LoadingOrder.after("1"), "3", "bad"));
    LoadingOrder.Orderable[] array = target.toArray(new LoadingOrder.Orderable[target.size()]);
    checkSortingFailure(array);
  }

  private LoadingOrder.Orderable createElement(LoadingOrder order, @NonNls String idString, @NonNls String elementId) {
    Mock mock = new Mock(LoadingOrder.Orderable.class);
    mock.stubs().method("getOrder").withNoArguments().will(new ReturnStub(order));
    mock.stubs().method("getOrderId").withNoArguments().will(returnValue(idString));
    mock.stubs().method("getDescribingElement").withNoArguments().will(new ReturnStub(new MyElement(elementId)));
    return (LoadingOrder.Orderable) mock.proxy();
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
