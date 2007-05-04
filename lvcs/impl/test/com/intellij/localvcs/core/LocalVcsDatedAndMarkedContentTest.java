package com.intellij.localvcs.core;

import com.intellij.localvcs.integration.RevisionTimestampComparator;
import org.junit.Test;

public class LocalVcsDatedAndMarkedContentTest extends LocalVcsTestCase {
  LocalVcs vcs = new TestLocalVcs();

  @Test
  public void testGettingContent() {
    setCurrentTimestamp(10);
    vcs.createFile("f", ch("one"), -1);
    setCurrentTimestamp(20);
    vcs.changeFileContent("f", ch("two"), -1);

    assertNull(vcs.getByteContent("f", comparator(5)));
    assertEquals("one", new String(vcs.getByteContent("f", comparator(10))));
    assertNull(vcs.getByteContent("f", comparator(15)));

    assertEquals("two", new String(vcs.getByteContent("f", comparator(20))));
    assertNull(vcs.getByteContent("f", comparator(100)));
  }

  @Test
  public void testGettingMostRecentRevisionContent() {
    setCurrentTimestamp(10);
    vcs.createFile("f", ch("one"), -1);
    setCurrentTimestamp(20);
    vcs.changeFileContent("f", ch("two"), -1);

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
    vcs.createFile("f", bigContentHolder(), -1);

    assertNull(vcs.getByteContent("f", comparator(10)));
  }

  @Test
  public void testGettingContentIfPurgedIsNull() {
    setCurrentTimestamp(10);
    vcs.createFile("f", ch("one"), -1);
    setCurrentTimestamp(20);
    vcs.changeFileContent("f", ch("two"), -1);

    vcs.purgeObsolete(5);

    assertNull(vcs.getByteContent("f", comparator(10)));
    assertEquals("two", new String(vcs.getByteContent("f", comparator(20))));
  }

  private RevisionTimestampComparator comparator(long timestamp) {
    return new TestTimestampComparator(timestamp);
  }

  @Test
  public void testMarkingFiles() {
    vcs.createFile("f", ch("one"), -1);
    vcs.mark("f");
    vcs.changeFileContent("f", ch("two"), -1);

    assertEquals("one", new String(vcs.getLastMarkedByteContent("f")));
  }

  @Test
  public void testMarkedContentForUnmarkedFileIsNull() {
    vcs.createFile("f", ch("one"), -1);
    vcs.changeFileContent("f", ch("two"), -1);

    assertNull(vcs.getLastMarkedByteContent("f"));
  }

  @Test
  public void testMarkedContentBigFileIsNull() {
    vcs.createFile("f", bigContentHolder(), -1);
    vcs.mark("f");
    vcs.changeFileContent("f", ch("two"), -1);

    assertNull(vcs.getLastMarkedByteContent("f"));
  }

  @Test
  public void testTakingLastMarkedContent() {
    vcs.createFile("f", ch("one"), -1);
    vcs.mark("f");
    vcs.changeFileContent("f", ch("two"), -1);
    vcs.mark("f");
    vcs.changeFileContent("f", ch("three"), -1);

    assertEquals("two", new String(vcs.getLastMarkedByteContent("f")));
  }
}