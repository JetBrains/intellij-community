/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.extensions;

import org.jdom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Kireyev
 */
public abstract class LoadingOrder {
  public static final LoadingOrder ANY = new LoadingOrder("ANY") {
    int findPlace(Orderable[] orderables, int current) {
      return DONT_CARE;
    }
  };
  public static final LoadingOrder FIRST = new LoadingOrder("FIRST") {
    int findPlace(Orderable[] orderables, int current) {
      return SPECIAL;
    }
  };
  public static final LoadingOrder LAST = new LoadingOrder("LAST") {
    int findPlace(Orderable[] orderables, int current) {
      return SPECIAL;
    }
  };

  private static final int DONT_CARE = -1;
  static final int ACCEPTABLE = -2;
  private static final int SPECIAL = -3;

  private final String myName; // for debug only
  private static final String BEFORE_STR = "BEFORE:";
  private static final String AFTER_STR = "AFTER:";

  private LoadingOrder(String name) {
    myName = name;
  }

  public String toString() {
    return myName;
  }

  public static LoadingOrder before(final String id) {
    return new BeforeLoadingOrder(id);
  }

  public static LoadingOrder after(final String id) {
    return new AfterLoadingOrder(id);
  }

  abstract int findPlace(Orderable[] orderables, int current);

  public static void sort(Orderable[] orderables) {
    Orderable first = null;
    Orderable last = null;
    List other = new ArrayList();
    for (int i = 0; i < orderables.length; i++) {
      Orderable orderable = orderables[i];
      if (orderable.getOrder() == FIRST) {
        if (first != null) {
          throw new SortingException("More than one 'first' element", new Element[] {first.getDescribingElement(), orderable.getDescribingElement()});
        }
        first = orderable;
      }
      else if (orderable.getOrder() == LAST) {
        if (last != null) {
          throw new SortingException("More than one 'last' element", new Element[] {last.getDescribingElement(), orderable.getDescribingElement()});
        }
        last = orderable;
      }
      else {
        other.add(orderable);
      }
    }
    List result = new ArrayList();
    if (first != null) {
      result.add(first);
    }
    result.addAll(other);
    if (last != null) {
      result.add(last);
    }

    assert result.size() == orderables.length;

    Orderable[] presorted = (Orderable[]) result.toArray(new Orderable[result.size()]);

    int swapCount = 0;
    int maxSwaps = presorted.length * presorted.length;
    for (int i = 0; i < presorted.length; i++) {
      Orderable orderable = presorted[i];
      LoadingOrder order = orderable.getOrder();
      int place = order.findPlace(presorted, i);
      if (place == DONT_CARE || place == ACCEPTABLE || place == SPECIAL) {
        continue;
      }
      if (place == 0 && presorted[0].getOrder() == FIRST) {
        throw new SortingException("Element attempts to go before the specified first", new Element[] {orderable.getDescribingElement(), presorted[0].getDescribingElement()});
      }
      if (place == presorted.length - 1 && presorted[presorted.length - 1].getOrder() == LAST) {
        throw new SortingException("Element attempts to go after the specified last", new Element[] {orderable.getDescribingElement(), presorted[presorted.length - 1].getDescribingElement()});
      }
      moveTo(presorted, i, place);
      if (i > place) {
        i = place;
      }
      else {
        i--;
      }
      swapCount++;
      if (swapCount > maxSwaps) {
        List allElements = new ArrayList();
        for (int j = 0; j < presorted.length; j++) {
          allElements.add(presorted[j].getDescribingElement());
        }
        throw new SortingException("Could not satisfy sorting requirements", (Element[]) allElements.toArray(new Element[allElements.size()]));
      }
    }

    System.arraycopy(presorted, 0, orderables, 0, presorted.length);
  }

  private static void moveTo(Orderable[] orderables, int from, int to) {
    if (to == from) return;
    Orderable movedOrderable = orderables[from];
    if (to > from) {
      for (int i = from; i < to; i++) {
        orderables[i] = orderables[i + 1];
      }
    }
    else {
      for (int i = from; i > to; i--) {
        orderables[i] = orderables[i - 1];
      }
    }
    orderables[to] = movedOrderable;
  }

  public static LoadingOrder readOrder(String orderAttr) {
    if (orderAttr != null) {
      if ("FIRST".equalsIgnoreCase(orderAttr)) return FIRST;
      if ("LAST".equalsIgnoreCase(orderAttr)) return LAST;
      if ("ANY".equalsIgnoreCase(orderAttr)) return ANY;
      if (orderAttr.toUpperCase().startsWith(BEFORE_STR)) {
        return before(orderAttr.substring(BEFORE_STR.length()));
      }
      if (orderAttr.toUpperCase().startsWith(AFTER_STR)) {
        return after(orderAttr.substring(AFTER_STR.length()));
      }
    }
    return ANY;
  }

  private static class BeforeLoadingOrder extends LoadingOrder {
    private final String myId;

    public BeforeLoadingOrder(String id) {
      super(LoadingOrder.BEFORE_STR + id);
      myId = id;
    }

    int findPlace(Orderable[] orderables, int current) {
      for (int i = 0; i < orderables.length; i++) {
        Orderable orderable = orderables[i];
        String orderId = orderable.getOrderId();
        if (myId.equals(orderId)) {
          if (current < i) {
            return ACCEPTABLE;
          }
          else {
            return i;
          }
        }
      }
      return DONT_CARE;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof BeforeLoadingOrder)) return false;

      final BeforeLoadingOrder beforeLoadingOrder = (BeforeLoadingOrder) o;

      if (!myId.equals(beforeLoadingOrder.myId)) return false;

      return true;
    }

    public int hashCode() {
      return myId.hashCode();
    }
  }

  private static class AfterLoadingOrder extends LoadingOrder {
    private final String myId;

    public AfterLoadingOrder(String id) {
      super(LoadingOrder.AFTER_STR + id);
      myId = id;
    }

    int findPlace(Orderable[] orderables, int current) {
      for (int i = 0; i < orderables.length; i++) {
        Orderable orderable = orderables[i];
        String orderId = orderable.getOrderId();
        if (myId.equals(orderId)) {
          if (current > i) {
            return ACCEPTABLE;
          }
          else {
            return i;
          }
        }
      }
      return DONT_CARE;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof AfterLoadingOrder)) return false;

      final AfterLoadingOrder afterLoadingOrder = (AfterLoadingOrder) o;

      if (!myId.equals(afterLoadingOrder.myId)) return false;

      return true;
    }

    public int hashCode() {
      return myId.hashCode();
    }
  }

  public interface Orderable {
    String getOrderId();
    LoadingOrder getOrder();
    Element getDescribingElement();
  }
}
