package com.intellij.localvcs;

import org.junit.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public abstract class TestCase extends Assert {
  protected static IdPath idp(Integer... parts) {
    return new IdPath(parts);
  }

  protected static String p(String name) {
    return name;
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

  protected static void assertEntiesContents(String[] expectedContents, Collection<Entry> actualEntries) {
    List<String> actualContents = new ArrayList<String>();
    for (Entry rev : actualEntries) {
      actualContents.add(rev.getContent());
    }
    assertEquals(expectedContents, actualContents.toArray(new Object[0]));
  }
}
