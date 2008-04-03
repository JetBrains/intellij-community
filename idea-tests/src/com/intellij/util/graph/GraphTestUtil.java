package com.intellij.util.graph;

import junit.framework.Assert;

import java.util.Arrays;
import java.util.Iterator;

/**
 *  @author dsl
 */
public class GraphTestUtil {
  static <E> void assertIteratorsEqual(Iterator<E> expected, Iterator<E> found) {
    for (; expected.hasNext(); ) {
      Assert.assertTrue(found.hasNext());
      Assert.assertEquals(expected.next(), found.next());
    }
    Assert.assertFalse(found.hasNext());
  }

  public static Iterator<TestNode> iteratorOfArray(TestNode[] array) {
    return Arrays.asList(array).iterator();
  }
}
