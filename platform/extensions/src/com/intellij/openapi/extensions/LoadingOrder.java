// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.graph.InboundSemiGraph;
import org.jetbrains.annotations.ApiStatus;
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
@ApiStatus.Internal
public final class LoadingOrder {
  @NonNls public static final String FIRST_STR = "first";
  @NonNls public static final String LAST_STR = "last";
  @NonNls public static final String BEFORE_STR = "before ";
  @NonNls public static final String BEFORE_STR_OLD = "before:";
  @NonNls public static final String AFTER_STR = "after ";
  @NonNls public static final String AFTER_STR_OLD = "after:";

  @NonNls public static final String ORDER_RULE_SEPARATOR = ",";

  public static final LoadingOrder ANY = new LoadingOrder();
  public static final LoadingOrder FIRST = new LoadingOrder(FIRST_STR);
  public static final LoadingOrder LAST = new LoadingOrder(LAST_STR);

  // for debug only
  @NonNls private final String myName;

  private final boolean myFirst;
  private final boolean myLast;
  private final Set<String> myBefore;
  private final Set<String> myAfter;

  private LoadingOrder() {
    myName = "ANY";
    myFirst = false;
    myLast = false;
    myBefore = Collections.emptySet();
    myAfter = Collections.emptySet();
  }

  public LoadingOrder(@NonNls @NotNull String text) {
    myName = text;
    boolean last = false;
    boolean first = false;
    Set<String> before = null;
    Set<String> after = null;
    for (String string : text.split(ORDER_RULE_SEPARATOR)) {
      String trimmed = string.trim();
      if (trimmed.equalsIgnoreCase(FIRST_STR)) {
        first = true;
      }
      else if (trimmed.equalsIgnoreCase(LAST_STR)) {
        last = true;
      }
      else if (StringUtilRt.startsWithIgnoreCase(trimmed, BEFORE_STR)) {
        if (before == null) {
          before = new LinkedHashSet<>(2);
        }
        before.add(trimmed.substring(BEFORE_STR.length()).trim());
      }
      else if (StringUtilRt.startsWithIgnoreCase(trimmed, BEFORE_STR_OLD)) {
        if (before == null) {
          before = new LinkedHashSet<>(2);
        }
        before.add(trimmed.substring(BEFORE_STR_OLD.length()).trim());
      }
      else if (StringUtilRt.startsWithIgnoreCase(trimmed, AFTER_STR)) {
        if (after == null) {
          after = new LinkedHashSet<>(2);
        }
        after.add(trimmed.substring(AFTER_STR.length()).trim());
      }
      else if (StringUtilRt.startsWithIgnoreCase(trimmed, AFTER_STR_OLD)) {
        if (after == null) {
          after = new LinkedHashSet<>(2);
        }
        after.add(trimmed.substring(AFTER_STR_OLD.length()).trim());
      }
      else {
        throw new AssertionError("Invalid specification: " + trimmed + "; should be one of FIRST, LAST, BEFORE <id> or AFTER <id>");
      }
    }
    myBefore = before == null ? Collections.emptySet() : before;
    myAfter = after == null ? Collections.emptySet() : after;
    myFirst = first;
    myLast = last;
  }

  @Override
  public String toString() {
    return myName;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof LoadingOrder)) {
      return false;
    }

    LoadingOrder that = (LoadingOrder)o;
    return myFirst == that.myFirst && myLast == that.myLast && myAfter.equals(that.myAfter) && myBefore.equals(that.myBefore);
  }

  @Override
  public int hashCode() {
    int result = myFirst ? 1 : 0;
    result = 31 * result + (myLast ? 1 : 0);
    result = 31 * result + myBefore.hashCode();
    result = 31 * result + myAfter.hashCode();
    return result;
  }

  public static LoadingOrder before(@NonNls String id) {
    return new LoadingOrder(BEFORE_STR + id);
  }

  public static LoadingOrder after(@NonNls String id) {
    return new LoadingOrder(AFTER_STR + id);
  }

  public static void sort(Orderable @NotNull [] orderable) {
    if (orderable.length > 1) {
      sort(Arrays.asList(orderable));
    }
  }

  public static void sort(@NotNull List<? extends Orderable> orderable) {
    if (orderable.size() < 2) {
      return;
    }

    // our graph is pretty sparse so do benefit from the fact
    final Map<String, Orderable> map = new LinkedHashMap<>();
    final Map<Orderable, LoadingOrder> cachedMap = new LinkedHashMap<>();
    final Set<Orderable> first = new LinkedHashSet<>(1);
    final Set<Orderable> hasBefore = new LinkedHashSet<>(orderable.size());
    for (Orderable o : orderable) {
      String id = o.getOrderId();
      if (id != null && !id.isEmpty()) {
        map.put(id, o);
      }
      LoadingOrder order = o.getOrder();
      if (order == ANY) {
        continue;
      }

      cachedMap.put(o, order);
      if (order.myFirst) {
        first.add(o);
      }
      if (!order.myBefore.isEmpty()) {
        hasBefore.add(o);
      }
    }

    if (cachedMap.isEmpty()) {
      return;
    }

    InboundSemiGraph<Orderable> graph = new InboundSemiGraph<Orderable>() {
      @Override
      public @NotNull Collection<Orderable> getNodes() {
        List<Orderable> list = new ArrayList<>(orderable);
        Collections.reverse(list);
        return list;
      }

      @Override
      public @NotNull Iterator<Orderable> getIn(Orderable n) {
        LoadingOrder order = cachedMap.getOrDefault(n, ANY);

        Set<Orderable> predecessors = new LinkedHashSet<>();
        for (String id : order.myAfter) {
          Orderable o = map.get(id);
          if (o != null) {
            predecessors.add(o);
          }
        }

        String id = n.getOrderId();
        if (id != null && !id.isEmpty()) {
          for (Orderable o : hasBefore) {
            LoadingOrder hisOrder = cachedMap.getOrDefault(o, ANY);
            if (hisOrder.myBefore.contains(id)) {
              predecessors.add(o);
            }
          }
        }

        if (order.myLast) {
          for (Orderable o : orderable) {
            LoadingOrder hisOrder = cachedMap.getOrDefault(o, ANY);
            if (!hisOrder.myLast) {
              predecessors.add(o);
            }
          }
        }

        if (!order.myFirst) {
          predecessors.addAll(first);
        }

        return predecessors.iterator();
      }
    };

    DFSTBuilder<Orderable> builder = new DFSTBuilder<>(GraphGenerator.generate(CachingSemiGraph.cache(graph)));
    if (!builder.isAcyclic()) {
      Map.Entry<Orderable, Orderable> p = Objects.requireNonNull(builder.getCircularDependency());
      throw new SortingException("Could not satisfy sorting requirements", p.getKey(), p.getValue());
    }

    orderable.sort(builder.comparator());
  }

  public static @NotNull LoadingOrder readOrder(@Nullable String orderAttr) {
    if (orderAttr == null) {
      return ANY;
    }
    else if (orderAttr.equals(FIRST_STR)) {
      return FIRST;
    }
    else if (orderAttr.equals(LAST_STR)) {
      return LAST;
    }
    else {
      return new LoadingOrder(orderAttr);
    }
  }

  public interface Orderable {
    @Nullable
    String getOrderId();

    LoadingOrder getOrder();
  }
}
