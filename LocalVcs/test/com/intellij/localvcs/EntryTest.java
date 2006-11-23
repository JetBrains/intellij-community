package com.intellij.localvcs;

import org.junit.Test;

public class EntryTest extends TestCase {
  @Test
  public void testOutdated() {
    Entry e = new MyEntry(2L);
    assertTrue(e.isOutdated(1L));
    assertTrue(e.isOutdated(3L));

    assertFalse(e.isOutdated(2L));
  }

  private class MyEntry extends Entry {
    public MyEntry(Long timestamp) {
      super(null, timestamp);
    }

    @Override
    public String getName() {
      throw new UnsupportedOperationException();
    }

    @Override
    protected Entry findEntry(Matcher m) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Entry copy() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Entry renamed(String newName, Long timestamp) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Difference getDifferenceWith(Entry e) {
      throw new UnsupportedOperationException();
    }

    @Override
    protected Difference asCreatedDifference() {
      throw new UnsupportedOperationException();
    }

    @Override
    protected Difference asDeletedDifference() {
      throw new UnsupportedOperationException();
    }
  }
}
