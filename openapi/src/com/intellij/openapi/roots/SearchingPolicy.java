/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.util.Condition;

/**
 * @author dsl
 */
public class SearchingPolicy extends RootPolicy<OrderEntry>{
  private final Condition<OrderEntry> myCondition;

  public SearchingPolicy(Condition<OrderEntry> condition) {
    myCondition = condition;
  }

  public OrderEntry visitOrderEntry(OrderEntry orderEntry, OrderEntry found) {
    if (found != null) return found;
    if (myCondition.value(orderEntry)) return orderEntry;
    return found;
  }
}
