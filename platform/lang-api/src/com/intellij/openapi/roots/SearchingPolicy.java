/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

  @Override
  public OrderEntry visitOrderEntry(OrderEntry orderEntry, OrderEntry found) {
    if (found != null) return found;
    if (myCondition.value(orderEntry)) return orderEntry;
    return found;
  }
}
