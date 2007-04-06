package com.intellij.localvcs;

import org.junit.Assert;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public abstract class LocalVcsTestCase extends Assert {
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
    return cs(null, changes);
  }

  protected static ChangeSet cs(String label, Change... changes) {
    return cs(0, label, changes);
  }

  protected static ChangeSet cs(long timestamp, Change... changes) {
    return cs(timestamp, null, changes);
  }

  protected static ChangeSet cs(long timestamp, String label, Change... changes) {
    return new ChangeSet(timestamp, label, Arrays.asList(changes));
  }

  protected void setCurrentTimestamp(long t) {
    Clock.setCurrentTimestamp(t);
  }

  protected static void assertEquals(Object[] expected, Collection actual) {
    List<Object> expectedList = Arrays.asList(expected);
    String message = notEqualsMessage(expectedList, actual);

    assertTrue(message, expectedList.size() == actual.size());
    assertTrue(message, actual.containsAll(expectedList));
  }

  protected static void assertEquals(byte[] expected, byte[] actual) {
    String message = notEqualsMessage(expected, actual);

    assertTrue(message, expected.length == actual.length);
    for (int i = 0; i < expected.length; i++) {
      assertTrue(message, expected[i] == actual[i]);
    }
  }

  protected void assertEquals(long expected, long actual) {
    assertEquals((Object)expected, (Object)actual);
  }

  private static String notEqualsMessage(Object expected, Object actual) {
    return "elements are not equal:\n" + "\texpected: " + expected + "\n" + "\tactual: " + actual;
  }
}
