/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.testFramework;

import com.intellij.util.Consumer;
import junit.framework.AssertionFailedError;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * @author peter
 */
public abstract class UsefulTestCase extends TestCase {

  @NonNls
  public static String toString(Collection collection) {
    if (collection.isEmpty()) {
      return "<empty>";
    }

    final StringBuilder builder = new StringBuilder();
    for (final Object o : collection) {
      builder.append(o);
      builder.append("\n");
    }
    return builder.toString();
  }

  public static <T> void assertOrderedEquals(Collection<T> actual, T... expected) {
    assertNotNull(actual);
    assertNotNull(expected);
    final List<T> expectedList = Arrays.asList(expected);
    if (!new ArrayList<T>(actual).equals(expectedList)) {
      assertEquals(toString(actual), toString(expectedList));
      fail();
    }
  }

  public static <T> void assertOrderedCollection(Collection<T> collection, Consumer<T>... checkers) {
    assertNotNull(collection);
    if (collection.size() != checkers.length) {
      fail(toString(collection));
    }
    int i = 0;
    for (final T actual : collection) {
      try {
        checkers[i].consume(actual);
      }
      catch (AssertionFailedError e) {
        System.out.println(i + ": " + actual);
        throw e;
      }
      i++;
    }
  }

  public static <T> void assertUnorderedCollection(Collection<T> collection, Consumer<T>... checkers) {
    assertNotNull(collection);
    if (collection.size() != checkers.length) {
      fail(toString(collection));
    }
    Set<Consumer<T>> checkerSet = new HashSet<Consumer<T>>(Arrays.asList(checkers));
    int i = 0;
    for (final T actual : collection) {
      boolean flag = true;
      for (final Consumer<T> condition : checkerSet) {
        if (accepts(condition, actual)) {
          checkerSet.remove(condition);
          flag = false;
          break;
        }
      }
      if (flag) {
        fail("Incorrect element(" + i + "): " + actual);
      }
      i++;
    }
  }

  private static <T> boolean accepts(final Consumer<T> condition, final T actual) {
    try {
      condition.consume(actual);
      return true;
    }
    catch (Throwable e) {
      return false;
    }
  }

  public static <T> T assertInstanceOf(Object o, Class<T> aClass) {
    assertNotNull(o);
    assertTrue(o.getClass().getName(), aClass.isInstance(o));
    return (T)o;
  }

  public static <T> T assertOneElement(Collection<T> collection) {
    assertNotNull(collection);
    assertEquals(1, collection.size());
    return collection.iterator().next();
  }

  public static <T> T assertOneElement(T[] ts) {
    assertNotNull(ts);
    assertEquals(1, ts.length);
    return ts[0];
  }

  protected void printThreadDump() {
    final Map<Thread,StackTraceElement[]> traces = Thread.getAllStackTraces();
    for (final Map.Entry<Thread, StackTraceElement[]> entry : traces.entrySet()) {
      System.out.println("\n" + entry.getKey().getName() + "\n");
      final StackTraceElement[] value = entry.getValue();
      for (final StackTraceElement stackTraceElement : value) {
        System.out.println(stackTraceElement);
      }
    }
  }
}
