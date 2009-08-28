package com.intellij.util.diff;

import com.intellij.util.ArrayUtil;
import junit.framework.TestCase;

import java.util.ArrayList;

/**
 * @author dyoma
 */
public class DiffTest extends TestCase {
  private static final Object[] DATA_123 = new Object[]{"1", "2", "3"};
  private static final Object[] DATA_AB = new Object[]{"a", "b"};
  private static final Object[] DATA_12AB23 = new Object[]{"1", "2", "a", "b", "2", "3"};

  public void testEqual() {
    Diff.Change change = Diff.buildChanges(DATA_123, DATA_123);
    assertNull(change);
  }

  public void testCompletelyDifferent() {
    Diff.Change change = Diff.buildChanges(DATA_AB, DATA_123);
    IntLCSTest.checkLastChange(change, 0, 0, 3, 2);
  }

  public void testSameMiddle() {
    Diff.Change change = Diff.buildChanges(DATA_123, new Object[]{"a", "2", "b"});
    IntLCSTest.checkChange(change, 0, 0, 1, 1);
    IntLCSTest.checkLastChange(change.link, 2, 2, 1, 1);
  }

  public void testOverlap() {
    Diff.Change change = Diff.buildChanges(DATA_123, DATA_12AB23);
    IntLCSTest.checkLastChange(change, 2, 2, 3, 0); // inserted:           AB2
  }

  public void testEqualUpToOneEnd() {
    Diff.Change change = Diff.buildChanges(DATA_AB, new Object[]{"a", "b", "1"});
    IntLCSTest.checkLastChange(change, 2, 2, 1, 0);
  }

  public void testEmptyAgainstSmth() {
    Object[] empty = ArrayUtil.EMPTY_OBJECT_ARRAY;
    Diff.Change change = Diff.buildChanges(empty, empty);
    assertNull(change);
    change = Diff.buildChanges(DATA_AB, empty);
    IntLCSTest.checkLastChange(change, 0, 0, 0, 2);
    change = Diff.buildChanges(empty, DATA_123);
    IntLCSTest.checkLastChange(change, 0, 0, 3, 0);
  }

  public void testPerfomance() {
    ArrayList first = new ArrayList();
    ArrayList second = new ArrayList();
    int max = 1000;
    for (int i = 0; i < max; i++) {
      first.add(Integer.toString(i));
      second.add(Integer.toString(max - i - 1));
    }
    Diff.buildChanges(first.toArray(), second.toArray());
    long start = System.currentTimeMillis();
    Diff.buildChanges(first.toArray(), second.toArray());
    System.out.println("Duration: " +(System.currentTimeMillis() - start));
  }

}
