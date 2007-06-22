package com.intellij.history.core.tree;

import com.intellij.history.core.LocalVcsTestCase;
import com.intellij.history.core.Paths;
import com.intellij.history.core.revisions.Difference;
import org.junit.Test;

public class EntryTest extends LocalVcsTestCase {
  @Test
  public void testPathEquality() {
    Entry e = new MyEntry() {
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
    public MyEntry() {
      super(-1, null);
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
