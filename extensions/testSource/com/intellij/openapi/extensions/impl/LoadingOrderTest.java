/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions.impl;

import org.jmock.cglib.MockObjectTestCase;
import org.jmock.cglib.Mock;
import org.jmock.core.stub.ReturnStub;
import org.jdom.Element;
import org.picocontainer.PicoContainer;
import org.picocontainer.defaults.AssignabilityRegistrationException;

import java.util.ArrayList;

import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.extensions.SortingException;

/**
 * @author Alexander Kireyev
 */
public class LoadingOrderTest extends MockObjectTestCase {
  public void testSimpleSorting() {
    ArrayList target = new ArrayList();
    target.add(createElement(LoadingOrder.ANY, null, "any"));
    target.add(createElement(LoadingOrder.FIRST, null, "1"));
    target.add(createElement(LoadingOrder.LAST, null, "2"));
    target.add(createElement(LoadingOrder.ANY, null, "any"));
    LoadingOrder.Orderable[] array = (LoadingOrder.Orderable[]) target.toArray(new LoadingOrder.Orderable[target.size()]);
    assertSequence(array, "1anyany2");
  }

  public void testComplexSorting() {
    ArrayList target = new ArrayList();
    String idOne = "idone";
    String idTwo = "idTwo";
    target.add(createElement(LoadingOrder.before(idTwo), idOne, "2"));
    target.add(createElement(LoadingOrder.FIRST, null, "0"));
    target.add(createElement(LoadingOrder.LAST, null, "5"));
    target.add(createElement(LoadingOrder.after(idTwo), null, "4"));
    target.add(createElement(LoadingOrder.ANY, idTwo, "3"));
    target.add(createElement(LoadingOrder.before(idOne), null, "1"));
    LoadingOrder.Orderable[] array = (LoadingOrder.Orderable[]) target.toArray(new LoadingOrder.Orderable[target.size()]);
    assertSequence(array, "012345");
  }

  public void testComplexSorting2() {
    ArrayList target = new ArrayList();
    String idOne = "idone";
    target.add(createElement(LoadingOrder.before(idOne), null, "2"));
    target.add(createElement(LoadingOrder.after(idOne), null, "4"));
    target.add(createElement(LoadingOrder.FIRST, null, "1"));
    target.add(createElement(LoadingOrder.ANY, idOne, "3"));
    target.add(createElement(LoadingOrder.ANY, null, "5"));
    target.add(createElement(LoadingOrder.LAST, null, "6"));
    LoadingOrder.Orderable[] array = (LoadingOrder.Orderable[]) target.toArray(new LoadingOrder.Orderable[target.size()]);
    assertSequence(array, "123456");
  }

  private void assertSequence(LoadingOrder.Orderable[] array, String expected) {
    LoadingOrder.sort(array);
    StringBuffer sequence = buildSequence(array);
    assertEquals(expected, sequence.toString());
  }

  private StringBuffer buildSequence(LoadingOrder.Orderable[] array) {
    StringBuffer sequence = new StringBuffer();
    for (int i = 0; i < array.length; i++) {
      LoadingOrder.Orderable adapter = array[i];
      sequence.append(((MyElement)adapter.getDescribingElement()).getID());
    }
    return sequence;
  }

  public void testFailingSortingBeforeFirst() {
    ArrayList target = new ArrayList();
    target.add(createElement(LoadingOrder.ANY, null, "good"));
    target.add(createElement(LoadingOrder.FIRST, "first", "bad"));
    target.add(createElement(LoadingOrder.LAST, null, "good"));
    target.add(createElement(LoadingOrder.before("first"), null, "bad"));
    LoadingOrder.Orderable[] array = (LoadingOrder.Orderable[]) target.toArray(new LoadingOrder.Orderable[target.size()]);
    checkSortingFailure(array, 2);
  }

  public void testFailingSortingFirst() {
    ArrayList target = new ArrayList();
    target.add(createElement(LoadingOrder.ANY, null, "good"));
    target.add(createElement(LoadingOrder.FIRST, "first", "bad"));
    target.add(createElement(LoadingOrder.LAST, null, "good"));
    target.add(createElement(LoadingOrder.FIRST, null, "bad"));
    LoadingOrder.Orderable[] array = (LoadingOrder.Orderable[]) target.toArray(new LoadingOrder.Orderable[target.size()]);
    checkSortingFailure(array, 2);
  }

  private void checkSortingFailure(LoadingOrder.Orderable[] array, int expectedCount) {
    try {
      LoadingOrder.sort(array);
      fail("Should have failed");
    }
    catch (SortingException e) {
      Element[] conflictingElements = e.getConflictingElements();
      assertEquals(expectedCount, conflictingElements.length);
      for (int i = 0; i < conflictingElements.length; i++) {
        MyElement conflictingElement = (MyElement) conflictingElements[i];
        assertEquals("bad", conflictingElement.getID());
      }
    }
  }

  public void testFailingSortingAfterLast() {
    ArrayList target = new ArrayList();
    target.add(createElement(LoadingOrder.after("last"), null, "bad"));
    target.add(createElement(LoadingOrder.FIRST, null, "good"));
    target.add(createElement(LoadingOrder.LAST, "last", "bad"));
    target.add(createElement(LoadingOrder.ANY, null, "good"));
    LoadingOrder.Orderable[] array = (LoadingOrder.Orderable[]) target.toArray(new LoadingOrder.Orderable[target.size()]);
    checkSortingFailure(array, 2);
  }

  public void testFailingSortingLast() {
    ArrayList target = new ArrayList();
    target.add(createElement(LoadingOrder.LAST, null, "bad"));
    target.add(createElement(LoadingOrder.FIRST, null, "good"));
    target.add(createElement(LoadingOrder.LAST, "last", "bad"));
    target.add(createElement(LoadingOrder.ANY, null, "good"));
    LoadingOrder.Orderable[] array = (LoadingOrder.Orderable[]) target.toArray(new LoadingOrder.Orderable[target.size()]);
    checkSortingFailure(array, 2);
  }

  public void testFailingSortingComplex() {
    ArrayList target = new ArrayList();
    target.add(createElement(LoadingOrder.after("2"), "1", "bad"));
    target.add(createElement(LoadingOrder.after("3"), "2", "bad"));
    target.add(createElement(LoadingOrder.after("1"), "3", "bad"));
    LoadingOrder.Orderable[] array = (LoadingOrder.Orderable[]) target.toArray(new LoadingOrder.Orderable[target.size()]);
    checkSortingFailure(array, 3);
  }

  private LoadingOrder.Orderable createElement(LoadingOrder order, String idString, String elementId) {
    Mock mock = new Mock(LoadingOrder.Orderable.class);
    mock.stubs().method("getOrder").withNoArguments().will(new ReturnStub(order));
    mock.stubs().method("getOrderId").withNoArguments().will(returnValue(idString));
    mock.stubs().method("getDescribingElement").withNoArguments().will(new ReturnStub(new MyElement(elementId)));
    return (LoadingOrder.Orderable) mock.proxy();
  }

  private static class MyElement extends Element {
    private String myID;

    public MyElement(String ID) {
      myID = ID;
    }

    public String getID() {
      return myID;
    }
  }
}
