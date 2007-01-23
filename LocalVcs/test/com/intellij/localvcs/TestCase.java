package com.intellij.localvcs;

import org.junit.Assert;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public abstract class TestCase extends Assert {
  protected static Content c(String data) {
    return new ByteContent(b(data));
  }
 
  protected static byte[] b(String data) {
    return data == null ? null : data.getBytes();
  }

  protected static <T> T[] a(T... objects) {
    return objects;
  }

  protected static IdPath idp(int... parts) {
    return new IdPath(parts);
  }

  protected static ChangeSet cs(Change... changes) {
    return new ChangeSet(Arrays.asList(changes));
  }

  @SuppressWarnings("unchecked")
  protected static void assertEquals(Object[] expected, Collection actual) {
    List<Object> expectedList = Arrays.asList(expected);
    String message = "elements are not equal:\n" + "\texpected: " + expectedList + "\n" + "\tactual: " + actual;

    assertTrue(message, expectedList.size() == actual.size());
    assertTrue(message, actual.containsAll(expectedList));
  }

  protected static void assertEquals(byte[] expected, byte[] actual) {
    assertEquals(expected.length, actual.length);
    for (int i = 0; i < expected.length; i++) {
      assertEquals(expected[i], actual[i]);
    }
  }
}
