package com.intellij.tools.build.bazel;

import junit.framework.TestCase;
import org.jetbrains.jps.util.Iterators;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Predicate;

public final class IteratorsTest extends TestCase {

  // --- isEmpty / isEmptyCollection ---

  public void testIsEmpty_null() {
    assertTrue(Iterators.isEmpty(null));
  }

  public void testIsEmpty_emptyList() {
    assertTrue(Iterators.isEmpty(List.of()));
  }

  public void testIsEmpty_nonEmpty() {
    assertFalse(Iterators.isEmpty(List.of(1, 2)));
  }

  public void testIsEmpty_nonCollectionIterable() {
    assertFalse(Iterators.isEmpty(Iterators.asIterable(42)));
  }

  public void testIsEmptyCollection_null() {
    assertTrue(Iterators.isEmptyCollection(null));
  }

  public void testIsEmptyCollection_emptyList() {
    assertTrue(Iterators.isEmptyCollection(List.of()));
  }

  public void testIsEmptyCollection_nonEmpty() {
    assertFalse(Iterators.isEmptyCollection(List.of(1)));
  }

  public void testIsEmptyCollection_nonCollectionIterable() {
    // non-Collection iterables are not recognized as empty collections
    assertFalse(Iterators.isEmptyCollection(Iterators.asIterable(42)));
  }

  // --- contains ---

  public void testContains_null() {
    assertFalse(Iterators.contains(null, "x"));
  }

  public void testContains_emptyList() {
    assertFalse(Iterators.contains(List.of(), "x"));
  }

  public void testContains_present() {
    assertTrue(Iterators.contains(List.of("a", "b", "c"), "b"));
  }

  public void testContains_absent() {
    assertFalse(Iterators.contains(List.of("a", "b"), "z"));
  }

  public void testContains_collection() {
    assertTrue(Iterators.contains(new HashSet<>(List.of(1, 2, 3)), 2));
  }

  // --- count ---

  public void testCount_null() {
    assertEquals(0, Iterators.count(null));
  }

  public void testCount_emptyList() {
    assertEquals(0, Iterators.count(List.of()));
  }

  public void testCount_collection() {
    assertEquals(3, Iterators.count(List.of("a", "b", "c")));
  }

  public void testCount_nonCollectionIterable() {
    assertEquals(1, Iterators.count(Iterators.asIterable(42)));
  }

  // --- find ---

  public void testFind_null() {
    assertNull(Iterators.find(null, e -> true));
  }

  public void testFind_emptyList() {
    assertNull(Iterators.find(List.of(), e -> true));
  }

  public void testFind_found() {
    assertEquals("bb", Iterators.find(List.of("a", "bb", "ccc"), s -> s.length() == 2));
  }

  public void testFind_notFound() {
    assertNull(Iterators.find(List.of("a", "b"), s -> s.length() > 5));
  }

  public void testFind_returnsFirst() {
    assertEquals(2, (int)Iterators.find(List.of(1, 2, 3, 4), n -> n % 2 == 0));
  }

  // --- collect ---

  public void testCollect_nullIterable() {
    List<String> result = Iterators.collect((Iterable<String>)null, new ArrayList<>());
    assertEquals(List.of(), result);
  }

  public void testCollect_nullIterator() {
    List<String> result = Iterators.collect((Iterator<String>)null, new ArrayList<>());
    assertEquals(List.of(), result);
  }

  public void testCollect_elements() {
    List<Integer> result = Iterators.collect(List.of(1, 2, 3), new ArrayList<>());
    assertEquals(List.of(1, 2, 3), result);
  }

  public void testCollect_intoSet() {
    Set<Integer> result = Iterators.collect(List.of(1, 2, 2, 3), new HashSet<>());
    assertEquals(new HashSet<>(List.of(1, 2, 3)), result);
  }

  // --- flat (two iterables) ---

  public void testFlat_twoIterables() {
    List<Integer> result = Iterators.collect(
      Iterators.flat(List.of(1, 2), List.of(3, 4)),
      new ArrayList<>()
    );
    assertEquals(List.of(1, 2, 3, 4), result);
  }

  public void testFlat_firstEmpty() {
    List<Integer> result = Iterators.collect(
      Iterators.flat(List.of(), List.of(3, 4)),
      new ArrayList<>()
    );
    assertEquals(List.of(3, 4), result);
  }

  public void testFlat_secondEmpty() {
    List<Integer> result = Iterators.collect(
      Iterators.flat(List.of(1, 2), List.of()),
      new ArrayList<>()
    );
    assertEquals(List.of(1, 2), result);
  }

  public void testFlat_bothEmpty() {
    List<Integer> result = Iterators.collect(
      Iterators.flat(List.of(), List.of()),
      new ArrayList<>()
    );
    assertEquals(List.of(), result);
  }

  public void testFlat_firstNull() {
    List<Integer> result = Iterators.collect(
      Iterators.flat(null, List.of(1, 2)),
      new ArrayList<>()
    );
    assertEquals(List.of(1, 2), result);
  }

  public void testFlat_secondNull() {
    List<Integer> result = Iterators.collect(
      Iterators.flat(List.of(1, 2), null),
      new ArrayList<>()
    );
    assertEquals(List.of(1, 2), result);
  }

  // --- flat (collection of iterables) ---

  public void testFlat_collectionOfIterables() {
    List<Integer> result = Iterators.collect(
      Iterators.flat(List.of(List.of(1, 2), List.of(3), List.of(4, 5))),
      new ArrayList<>()
    );
    assertEquals(List.of(1, 2, 3, 4, 5), result);
  }

  public void testFlat_collectionEmpty() {
    List<Integer> result = Iterators.collect(
      Iterators.flat(List.<Iterable<Integer>>of()),
      new ArrayList<>()
    );
    assertEquals(List.of(), result);
  }

  public void testFlat_collectionSingleElement() {
    List<Integer> result = Iterators.collect(
      Iterators.flat(List.of(List.of(1, 2))),
      new ArrayList<>()
    );
    assertEquals(List.of(1, 2), result);
  }

  public void testFlat_collectionWithEmptyParts() {
    List<Integer> result = Iterators.collect(
      Iterators.flat(List.of(List.of(), List.of(1), List.of(), List.of(2))),
      new ArrayList<>()
    );
    assertEquals(List.of(1, 2), result);
  }

  // --- flat (iterator of iterators) ---

  public void testFlat_iteratorOfIterators() {
    Iterator<Iterator<Integer>> groups = List.of(
      List.of(1, 2).iterator(),
      List.of(3).iterator()
    ).iterator();
    List<Integer> result = Iterators.collect(Iterators.flat(groups), new ArrayList<>());
    assertEquals(List.of(1, 2, 3), result);
  }

  public void testFlat_iteratorOfIterators_withEmptyGroups() {
    Iterator<Iterator<String>> groups = Arrays.asList(
      Collections.<String>emptyIterator(),
      List.of("a").iterator(),
      Collections.<String>emptyIterator(),
      List.of("b", "c").iterator(),
      Collections.<String>emptyIterator()
    ).iterator();
    List<String> result = Iterators.collect(Iterators.flat(groups), new ArrayList<>());
    assertEquals(List.of("a", "b", "c"), result);
  }

  // --- asIterator / asIterable ---

  public void testAsIterator_singleElement() {
    Iterator<String> it = Iterators.asIterator("hello");
    assertTrue(it.hasNext());
    assertEquals("hello", it.next());
    assertFalse(it.hasNext());
  }

  public void testAsIterator_singleElement_throwsAfterExhausted() {
    //noinspection WriteOnlyObject
    Iterator<String> it = Iterators.asIterator("x");
    it.next();
    try {
      it.next();
      fail("Expected NoSuchElementException");
    }
    catch (NoSuchElementException ignored) {
    }
  }

  public void testAsIterator_nullIterable() {
    Iterator<String> it = Iterators.asIterator(null);
    assertFalse(it.hasNext());
  }

  public void testAsIterable_singleElement() {
    List<String> result = Iterators.collect(Iterators.asIterable("x"), new ArrayList<>());
    assertEquals(List.of("x"), result);
  }

  public void testAsIterable_array() {
    List<Integer> result = Iterators.collect(Iterators.asIterable(new Integer[]{1, 2, 3}), new ArrayList<>());
    assertEquals(List.of(1, 2, 3), result);
  }

  public void testAsIterable_nullArray() {
    List<Integer> result = Iterators.collect(Iterators.asIterable(null), new ArrayList<>());
    assertEquals(List.of(), result);
  }

  // --- reverse ---

  public void testReverse() {
    List<Integer> result = Iterators.collect(Iterators.reverse(List.of(1, 2, 3)), new ArrayList<>());
    assertEquals(List.of(3, 2, 1), result);
  }

  public void testReverse_emptyList() {
    List<Integer> result = Iterators.collect(Iterators.reverse(List.of()), new ArrayList<>());
    assertEquals(List.of(), result);
  }

  public void testReverse_singleElement() {
    List<String> result = Iterators.collect(Iterators.reverse(List.of("a")), new ArrayList<>());
    assertEquals(List.of("a"), result);
  }

  // --- map ---

  public void testMap_iterable() {
    List<Integer> result = Iterators.collect(
      Iterators.map(List.of("a", "bb", "ccc"), String::length),
      new ArrayList<>()
    );
    assertEquals(List.of(1, 2, 3), result);
  }

  public void testMap_emptyIterable() {
    List<Integer> result = Iterators.collect(
      Iterators.map(List.of(), String::length),
      new ArrayList<>()
    );
    assertEquals(List.of(), result);
  }

  public void testMap_nullIterable() {
    List<Integer> result = Iterators.collect(
      Iterators.map((Iterable<String>)null, String::length),
      new ArrayList<>()
    );
    assertEquals(List.of(), result);
  }

  public void testMap_iterator() {
    List<String> result = Iterators.collect(
      Iterators.map(List.of(1, 2, 3).iterator(), n -> "n" + n),
      new ArrayList<>()
    );
    assertEquals(List.of("n1", "n2", "n3"), result);
  }

  // --- filter ---

  public void testFilter_iterable() {
    List<Integer> result = Iterators.collect(
      Iterators.filter(List.of(1, 2, 3, 4, 5), n -> n % 2 == 0),
      new ArrayList<>()
    );
    assertEquals(List.of(2, 4), result);
  }

  public void testFilter_noMatch() {
    List<Integer> result = Iterators.collect(
      Iterators.filter(List.of(1, 3, 5), n -> n % 2 == 0),
      new ArrayList<>()
    );
    assertEquals(List.of(), result);
  }

  public void testFilter_allMatch() {
    List<Integer> result = Iterators.collect(
      Iterators.filter(List.of(2, 4, 6), n -> n % 2 == 0),
      new ArrayList<>()
    );
    assertEquals(List.of(2, 4, 6), result);
  }

  public void testFilter_emptyIterable() {
    List<Integer> result = Iterators.collect(
      Iterators.filter(List.of(), n -> true),
      new ArrayList<>()
    );
    assertEquals(List.of(), result);
  }

  public void testFilter_nullIterable() {
    List<Integer> result = Iterators.collect(
      Iterators.filter((Iterable<Integer>)null, n -> true),
      new ArrayList<>()
    );
    assertEquals(List.of(), result);
  }

  public void testFilter_nextWithoutHasNext() {
    Iterator<Integer> it = Iterators.filter(List.of(1, 2, 3).iterator(), n -> n >= 2);
    // calling next() without hasNext() should still work
    assertEquals(2, (int)it.next());
    assertEquals(3, (int)it.next());
  }

  // --- filterWithOrder ---

  public void testFilterWithOrder_groupedByPredicateOrder() {
    List<String> items = List.of("a1", "b1", "c1", "a2", "b2");
    List<Predicate<String>> predicates = List.of(
      s -> s.startsWith("b"),
      s -> s.startsWith("a"),
      s -> s.startsWith("c")
    );
    List<String> result = Iterators.collect(Iterators.filterWithOrder(items, predicates), new ArrayList<>());
    // all b's first, then all a's, then all c's — each group preserves original order
    assertEquals(List.of("b1", "b2", "a1", "a2", "c1"), result);
  }

  public void testFilterWithOrder_predicateNotMatching() {
    List<String> items = List.of("apple", "banana");
    List<Predicate<String>> predicates = List.of(
      s -> s.startsWith("z"),
      s -> s.startsWith("a")
    );
    List<String> result = Iterators.collect(Iterators.filterWithOrder(items, predicates), new ArrayList<>());
    assertEquals(List.of("apple"), result);
  }

  public void testFilterWithOrder_predicateMatchesMultipleElements() {
    // each predicate selects ALL matching elements in their original order;
    // overall output is grouped by predicate sequence
    List<Integer> items = List.of(1, 2, 3, 4, 5, 6);
    Predicate<Integer> even = n -> n % 2 == 0;
    Predicate<Integer> odd = n -> n % 2 != 0;
    List<Predicate<Integer>> predicates = List.of(even, odd);
    List<Integer> result = Iterators.collect(Iterators.filterWithOrder(items, predicates), new ArrayList<>());
    // all evens first (in original order), then all odds (in original order)
    assertEquals(List.of(2, 4, 6, 1, 3, 5), result);
  }

  public void testFilterWithOrder_emptyPredicates() {
    List<String> result = Iterators.collect(
      Iterators.filterWithOrder(List.of("a", "b"), List.of()),
      new ArrayList<>()
    );
    assertEquals(List.of(), result);
  }

  public void testFilterWithOrder_emptyItems() {
    List<Predicate<String>> predicates = List.of(s -> true);
    List<String> result = Iterators.collect(
      Iterators.filterWithOrder(List.of(), predicates),
      new ArrayList<>()
    );
    assertEquals(List.of(), result);
  }

  // --- unique ---

  public void testUnique() {
    List<Integer> result = Iterators.collect(
      Iterators.unique(List.of(1, 2, 3, 2, 1, 4)),
      new ArrayList<>()
    );
    assertEquals(List.of(1, 2, 3, 4), result);
  }

  public void testUnique_alreadyUnique() {
    List<Integer> result = Iterators.collect(
      Iterators.unique(List.of(1, 2, 3)),
      new ArrayList<>()
    );
    assertEquals(List.of(1, 2, 3), result);
  }

  public void testUnique_empty() {
    List<Integer> result = Iterators.collect(
      Iterators.unique(List.of()),
      new ArrayList<>()
    );
    assertEquals(List.of(), result);
  }

  public void testUnique_null() {
    List<Integer> result = Iterators.collect(
      Iterators.unique((Iterable<Integer>)null),
      new ArrayList<>()
    );
    assertEquals(List.of(), result);
  }

  public void testUnique_allDuplicates() {
    List<String> result = Iterators.collect(
      Iterators.unique(List.of("a", "a", "a")),
      new ArrayList<>()
    );
    assertEquals(List.of("a"), result);
  }

  // --- equals ---

  public void testEquals_same() {
    assertTrue(Iterators.equals(List.of(1, 2, 3), List.of(1, 2, 3)));
  }

  public void testEquals_different() {
    assertFalse(Iterators.equals(List.of(1, 2, 3), List.of(1, 2, 4)));
  }

  public void testEquals_differentLength_firstLonger() {
    assertFalse(Iterators.equals(List.of(1, 2, 3), List.of(1, 2)));
  }

  public void testEquals_differentLength_secondLonger() {
    assertFalse(Iterators.equals(List.of(1, 2), List.of(1, 2, 3)));
  }

  public void testEquals_bothEmpty() {
    assertTrue(Iterators.equals(List.of(), List.of()));
  }

  public void testEquals_bothNull() {
    assertTrue(Iterators.equals(null, null));
  }

  public void testEquals_firstNull() {
    assertFalse(Iterators.equals(null, List.of(1)));
  }

  public void testEquals_secondNull() {
    assertFalse(Iterators.equals(List.of(1), null));
  }

  public void testEquals_sameInstance() {
    List<Integer> list = List.of(1, 2, 3);
    assertTrue(Iterators.equals(list, list));
  }

  public void testEquals_customComparator() {
    assertTrue(Iterators.equals(
      List.of("ABC", "DEF"),
      List.of("abc", "def"),
      (a, b) -> a.equalsIgnoreCase(b)
    ));
  }

  // --- hashCode ---

  public void testHashCode_consistentWithListHashCode() {
    List<String> list = List.of("a", "b", "c");
    assertEquals(list.hashCode(), Iterators.hashCode(list));
  }

  public void testHashCode_emptyList() {
    assertEquals(List.of().hashCode(), Iterators.hashCode(List.of()));
  }

  public void testHashCode_null() {
    assertEquals(0, Iterators.hashCode(null));
  }

  public void testHashCode_withNullElement() {
    List<String> list = Arrays.asList("a", null, "c");
    assertEquals(list.hashCode(), Iterators.hashCode(list));
  }

  // --- lazyIterable / lazyIterator ---

  public void testLazyIterable_defersCreation() {
    boolean[] called = {false};
    Iterable<Integer> lazy = Iterators.lazyIterable(() -> {
      called[0] = true;
      return List.of(1, 2, 3);
    });
    assertFalse(called[0]);
    List<Integer> result = Iterators.collect(lazy, new ArrayList<>());
    assertTrue(called[0]);
    assertEquals(List.of(1, 2, 3), result);
  }

  public void testLazyIterator_defersCreation() {
    boolean[] called = {false};
    Iterator<Integer> lazy = Iterators.lazyIterator(() -> {
      called[0] = true;
      return List.of(1, 2).iterator();
    });
    assertFalse(called[0]);
    List<Integer> result = Iterators.collect(lazy, new ArrayList<>());
    assertTrue(called[0]);
    assertEquals(List.of(1, 2), result);
  }

  // --- recurse ---

  public void testRecurse_visitsAllReachableNodes() {
    //   A
    //  / \
    // B   C
    // |
    // D
    Map<String, List<String>> graph = new HashMap<>();
    graph.put("A", List.of("B", "C"));
    graph.put("B", List.of("D"));
    graph.put("C", List.of());
    graph.put("D", List.of());

    List<String> result = Iterators.collect(
      Iterators.recurse("A", node -> graph.getOrDefault(node, List.of()), true),
      new ArrayList<>()
    );

    assertEquals(List.of("A", "B", "C", "D"), result);
  }

  public void testRecurse_visitsAllNodesInChain() {
    Map<String, List<String>> graph = new HashMap<>();
    graph.put("A", List.of("B"));
    graph.put("B", List.of("C"));
    graph.put("C", List.of("D"));
    graph.put("D", List.of());

    List<String> result = Iterators.collect(
      Iterators.recurse("A", node -> graph.getOrDefault(node, List.of()), true),
      new ArrayList<>()
    );

    assertEquals(List.of("A", "B", "C", "D"), result);
  }

  public void testRecurse_diamondGraph() {
    //   A
    //  / \
    // B   C
    //  \ /
    //   D
    Map<String, List<String>> graph = new HashMap<>();
    graph.put("A", List.of("B", "C"));
    graph.put("B", List.of("D"));
    graph.put("C", List.of("D"));
    graph.put("D", List.of());

    List<String> result = Iterators.collect(
      Iterators.recurse("A", node -> graph.getOrDefault(node, List.of()), true),
      new ArrayList<>()
    );

    // breadth-first: A, siblings B and C, then D (reached via B; C's reference to D is skipped as already visited)
    assertEquals(List.of("A", "B", "C", "D"), result);
  }

  public void testRecurse_cycle() {
    Map<String, List<String>> graph = new HashMap<>();
    graph.put("A", List.of("B"));
    graph.put("B", List.of("A"));

    List<String> result = Iterators.collect(
      Iterators.recurse("A", node -> graph.getOrDefault(node, List.of()), true),
      new ArrayList<>()
    );

    assertEquals(List.of("A", "B"), result);
  }

  public void testRecurse_excludeHead() {
    Map<String, List<String>> graph = new HashMap<>();
    graph.put("A", List.of("B", "C"));
    graph.put("B", List.of());
    graph.put("C", List.of());

    List<String> result = Iterators.collect(
      Iterators.recurse("A", node -> graph.getOrDefault(node, List.of()), false),
      new ArrayList<>()
    );

    assertEquals(List.of("B", "C"), result);
  }

  public void testRecurse_leafNode() {
    List<String> result = Iterators.collect(
      Iterators.recurse("A", node -> List.of(), true),
      new ArrayList<>()
    );

    assertEquals(List.of("A"), result);
  }

  // --- recurseDepth ---

  public void testRecurseDepth_visitsDepthFirst() {
    //   A
    //  / \
    // B   C
    // |
    // D
    Map<String, List<String>> graph = new HashMap<>();
    graph.put("A", List.of("B", "C"));
    graph.put("B", List.of("D"));
    graph.put("C", List.of());
    graph.put("D", List.of());

    List<String> result = Iterators.collect(
      Iterators.recurseDepth("A", node -> graph.getOrDefault(node, List.of()), true),
      new ArrayList<>()
    );

    // depth-first: A, then B subtree (B, D), then C
    assertEquals(List.of("A", "B", "D", "C"), result);
  }

  public void testRecurseDepth_cycle() {
    Map<String, List<String>> graph = new HashMap<>();
    graph.put("A", List.of("B"));
    graph.put("B", List.of("A"));

    List<String> result = Iterators.collect(
      Iterators.recurseDepth("A", node -> graph.getOrDefault(node, List.of()), true),
      new ArrayList<>()
    );

    assertEquals(List.of("A", "B"), result);
  }

  public void testRecurseDepth_excludeHead() {
    Map<String, List<String>> graph = new HashMap<>();
    graph.put("A", List.of("B", "C"));
    graph.put("B", List.of());
    graph.put("C", List.of());

    List<String> result = Iterators.collect(
      Iterators.recurseDepth("A", node -> graph.getOrDefault(node, List.of()), false),
      new ArrayList<>()
    );

    assertFalse(result.contains("A"));
    assertEquals(List.of("B", "C"), result);
  }
}
