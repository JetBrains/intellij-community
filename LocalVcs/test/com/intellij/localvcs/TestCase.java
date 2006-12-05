package com.intellij.localvcs;

import org.junit.Assert;
import org.junit.Before;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public abstract class TestCase extends Assert {
  protected static <T> T[] a(T... objects) {
    return objects;
  }

  protected static IdPath idp(Integer... parts) {
    return new IdPath(parts);
  }

  protected static ChangeSet cs(Change... changes) {
    return new ChangeSet(Arrays.asList(changes));
  }

  @SuppressWarnings("unchecked")
  protected static void assertElements(Object[] expected, Collection actual) {
    List<Object> expectedList = Arrays.asList(expected);
    String message = "elements are not equal:\n" + "\texpected: " + expectedList + "\n" + "\tactual: " + actual;

    assertTrue(message, expectedList.size() == actual.size());
    assertTrue(message, actual.containsAll(expectedList));
  }
}
