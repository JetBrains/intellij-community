package com.intellij.history.core;

import com.intellij.history.integration.RevisionTimestampComparator;
import org.junit.Test;

public class LocalVcsDatedContentTest extends LocalVcsTestCase {
  LocalVcs vcs = new InMemoryLocalVcs();

  @Test
  public void testGettingContent() {
    setCurrentTimestamp(10);
    vcs.createFile("f", cf("one"), -1);
    setCurrentTimestamp(20);
    vcs.changeFileContent("f", cf("two"), -1);

    assertNull(vcs.getByteContent("f", comparator(5)));
    assertEquals("one", new String(vcs.getByteContent("f", comparator(10))));
    assertNull(vcs.getByteContent("f", comparator(15)));

    assertEquals("two", new String(vcs.getByteContent("f", comparator(20))));
    assertNull(vcs.getByteContent("f", comparator(100)));
  }

  @Test
  public void testGettingMostRecentRevisionContent() {
    setCurrentTimestamp(10);
    vcs.createFile("f", cf("one"), -1);
    setCurrentTimestamp(20);
    vcs.changeFileContent("f", cf("two"), -1);

    RevisionTimestampComparator c = new RevisionTimestampComparator() {
      public boolean isSuitable(long revisionTimestamp) {
        return revisionTimestamp < 100;
      }
    };
    assertEquals("two", new String(vcs.getByteContent("f", c)));
  }

  @Test
  public void testGettingContentForUnavailableContentIsNull() {
    setCurrentTimestamp(10);
    vcs.createFile("f", bigContentFactory(), -1);

    assertNull(vcs.getByteContent("f", comparator(10)));
  }

  @Test
  public void testGettingContentIfPurgedIsNull() {
    setCurrentTimestamp(10);
    vcs.createFile("f", cf("one"), -1);
    setCurrentTimestamp(20);
    vcs.changeFileContent("f", cf("two"), -1);

    vcs.purgeObsolete(5);

    assertNull(vcs.getByteContent("f", comparator(10)));
    assertEquals("two", new String(vcs.getByteContent("f", comparator(20))));
  }

  private RevisionTimestampComparator comparator(long timestamp) {
    return new TestTimestampComparator(timestamp);
  }
}