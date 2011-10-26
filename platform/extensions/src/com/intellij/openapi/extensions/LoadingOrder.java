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
package com.intellij.openapi.extensions;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * All extensions can have an "order" attribute in their XML element that will affect the place where this extension will appear in the
 * {@link ExtensionPoint#getExtensions()}. Possible values are "first", "last", "before ID" and "after ID" where ID
 * is another same-type extension ID. Values can be combined in a comma-separated way. E.g. if you wish to plug before some extension XXX
 * that has "first" as its order, you must be "first, before XXX". The same with "last".<p>
 *
 * Extension ID can be specified in the "id" attribute of corresponding XML element. When you specify order, it's usually a good practice
 * to specify also id, to allow other plugin-writers to plug relatively to your extension.<p>
 *
 * If some anchor id can't be resolved, the constraint is ignored.
 *
 * @author Alexander Kireyev
 */
public class LoadingOrder {
  @NonNls private static final String FIRST_STR = "FIRST";
  @NonNls private static final String LAST_STR = "LAST";
  @NonNls private static final String BEFORE_STR = "BEFORE ";
  @NonNls private static final String BEFORE_STR_OLD = "BEFORE:";
  @NonNls private static final String AFTER_STR = "AFTER ";
  @NonNls private static final String AFTER_STR_OLD = "AFTER:";

  public static final LoadingOrder ANY = new LoadingOrder();
  public static final LoadingOrder FIRST = new LoadingOrder(FIRST_STR);
  public static final LoadingOrder LAST = new LoadingOrder(LAST_STR);

  @NonNls private final String myName; // for debug only
  private final boolean myFirst;
  private final boolean myLast;
  private final Set<String> myBefore = new HashSet<String>(2);
  private final Set<String> myAfter = new HashSet<String>(2);

  private LoadingOrder() {
    myName = "ANY";
    myFirst = false;
    myLast = false;
  }

  private LoadingOrder(@NonNls @NotNull String text) {
    myName = text;
    boolean last = false;
    boolean first = false;
    for (final String string : StringUtil.split(text, ",")) {
      String trimmed = string.trim();
      if (trimmed.equalsIgnoreCase(FIRST_STR)) first = true;
      else if (trimmed.equalsIgnoreCase(LAST_STR)) last = true;
      else if (StringUtil.startsWithIgnoreCase(trimmed, BEFORE_STR)) myBefore.add(trimmed.substring(BEFORE_STR.length()).trim());
      else if (StringUtil.startsWithIgnoreCase(trimmed, BEFORE_STR_OLD)) myBefore.add(trimmed.substring(BEFORE_STR_OLD.length()).trim());
      else if (StringUtil.startsWithIgnoreCase(trimmed, AFTER_STR)) myAfter.add(trimmed.substring(AFTER_STR.length()).trim());
      else if (StringUtil.startsWithIgnoreCase(trimmed, AFTER_STR_OLD)) myAfter.add(trimmed.substring(AFTER_STR_OLD.length()).trim());
      else throw new AssertionError("Invalid specification: " + trimmed + "; should be one of FIRST, LAST, BEFORE <id> or AFTER <id>");
    }
    myFirst = first;
    myLast = last;
  }

  public String toString() {
    return myName;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof LoadingOrder)) return false;

    final LoadingOrder that = (LoadingOrder)o;

    if (myFirst != that.myFirst) return false;
    if (myLast != that.myLast) return false;
    if (!myAfter.equals(that.myAfter)) return false;
    if (!myBefore.equals(that.myBefore)) return false;

    return true;
  }

  public int hashCode() {
    int result = myFirst ? 1 : 0;
    result = 31 * result + (myLast ? 1 : 0);
    result = 31 * result + myBefore.hashCode();
    result = 31 * result + myAfter.hashCode();
    return result;
  }

  public static LoadingOrder before(@NonNls final String id) {
    return new LoadingOrder(BEFORE_STR + id);
  }

  public static LoadingOrder after(@NonNls final String id) {
    return new LoadingOrder(AFTER_STR + id);
  }

  public static void sort(final Orderable[] orderables) {
    // our graph is pretty sparse so do benefit from the fact
    final Map<String,Orderable> map = new HashMap<String, Orderable>();
    final HashMap<Orderable, LoadingOrder> cachedMap = new HashMap<Orderable, LoadingOrder>(orderables.length);
    final HashSet<Orderable> first = new HashSet<Orderable>(1);
    final HashSet<Orderable> hasBefore = new HashSet<Orderable>(orderables.length);

    for(Orderable o:orderables) {
      final String id = o.getOrderId();
      if (StringUtil.isNotEmpty(id)) map.put(id, o);
      LoadingOrder order = o.getOrder();
      cachedMap.put(o, order);
      if (order.myFirst) first.add(o);
      if (order.myBefore.size() != 0) hasBefore.add(o);
    }

    DFSTBuilder<Orderable> builder = new DFSTBuilder<Orderable>(new GraphGenerator<Orderable>(new CachingSemiGraph<Orderable>(new GraphGenerator.SemiGraph<Orderable>() {
      @Override
      public Collection<Orderable> getNodes() {
        final ArrayList<Orderable> list = new ArrayList<Orderable>(Arrays.asList(orderables));
        Collections.reverse(list);
        return list;
      }

      @Override
      public Iterator<Orderable> getIn(final Orderable n) {
        final LoadingOrder order = cachedMap.get(n);

        Set<Orderable> predecessors = new LinkedHashSet<Orderable>();
        for (final String id : order.myAfter) {
          final Orderable orderable = map.get(id);
          if (orderable != null) {
            predecessors.add(orderable);
          }
        }

        String id = n.getOrderId();
        if (StringUtil.isNotEmpty(id)) {
          for (final Orderable orderable : hasBefore) {
            final LoadingOrder hisOrder = cachedMap.get(orderable);
            if (hisOrder.myBefore.contains(id)) {
              predecessors.add(orderable);
            }
          }
        }

        if (order.myLast) {
          for (final Orderable orderable : orderables) {
            final LoadingOrder hisOrder = cachedMap.get(orderable);
            if (!hisOrder.myLast) {
              predecessors.add(orderable);
            }
          }
        }

        if (!order.myFirst) {
          for(Orderable orderable:first) {
            predecessors.add(orderable);
          }
        }

        return predecessors.iterator();
      }
    })));

    if (!builder.isAcyclic()) {
      final Pair<Orderable,Orderable> dependency = builder.getCircularDependency();
      throw new SortingException("Could not satisfy sorting requirements", new Element[]{dependency.first.getDescribingElement(), dependency.second.getDescribingElement()});
    }

    Arrays.sort(orderables, builder.comparator());
  }

  public static LoadingOrder readOrder(@NonNls String orderAttr) {
    return orderAttr != null ? new LoadingOrder(orderAttr) : ANY;
  }

  public interface Orderable {
    @Nullable
    String getOrderId();
    LoadingOrder getOrder();
    Element getDescribingElement();
  }
}
