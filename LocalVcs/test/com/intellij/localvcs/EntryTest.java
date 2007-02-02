package com.intellij.localvcs;

import org.junit.Test;

public class EntryTest extends LocalVcsTestCase {
  @Test
  public void testOutdated() {
    Entry e = new MyEntry(2L);
    assertTrue(e.isOutdated(1L));
    assertTrue(e.isOutdated(3L));

    assertFalse(e.isOutdated(2L));
  }

  @Test
  public void testPathEquality() {
    Entry e = new MyEntry(null) {
      @Override
      public String getPath() {
        return "path";
      }
    };
    assertTrue(e.pathEquals("path"));
    assertFalse(e.pathEquals("bla-bla-bla"));

    Paths.setCaseSensitive(true);
    assertFalse(e.pathEquals("PATH"));

    Paths.setCaseSensitive(false);
    assertTrue(e.pathEquals("PATH"));
  }

  private class MyEntry extends Entry {
    public MyEntry(Long timestamp) {
      super(null, null, timestamp);
    }

    public Entry copy() {
      throw new UnsupportedOperationException();
    }

    public Difference getDifferenceWith(Entry e) {
      throw new UnsupportedOperationException();
    }

    protected Difference asCreatedDifference() {
      throw new UnsupportedOperationException();
    }

    protected Difference asDeletedDifference() {
      throw new UnsupportedOperationException();
    }
  }
}
