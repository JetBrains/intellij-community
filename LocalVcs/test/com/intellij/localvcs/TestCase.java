package com.intellij.localvcs;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Assert;

public class TestCase extends Assert {
  protected FileName fn(String name) {
    return new FileName(name);
  }

  protected ChangeSet cs(Change... changes) {
    return new ChangeSet(Arrays.asList(changes));
  }

  @SuppressWarnings("unchecked")
  protected void assertElements(Object[] expected, Collection actual) {
    assertEquals(expected.length, actual.size());
    assertTrue(actual.containsAll(Arrays.asList(expected)));
  }
}
