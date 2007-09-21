package com.intellij.history.core;

import com.intellij.history.FileRevisionTimestampComparator;
import org.junit.Test;

public class LocalVcsDatedContentTest extends LocalVcsTestCase {
  LocalVcs vcs = new InMemoryLocalVcs();

  @Test
  public void testGettingContentByRevisionTimestamp() {
    setCurrentTimestamp(10);
    vcs.createFile("f", cf("one"), -1, false);
    setCurrentTimestamp(20);
    vcs.changeFileContent("f", cf("two"), -1);

    assertNull(vcs.getByteContent("f", revisionComparator(5)));
    assertEquals("one", new String(vcs.getByteContent("f", revisionComparator(10))));
    assertNull(vcs.getByteContent("f", revisionComparator(15)));

    assertEquals("two", new String(vcs.getByteContent("f", revisionComparator(20))));
    assertNull(vcs.getByteContent("f", revisionComparator(100)));
  }

  @Test
  public void testGettingContentStampByFileTimestamp() {
    vcs.createFile("f", cf("one"), 10, false);
    vcs.changeFileContent("f", cf("two"), 20);
    vcs.changeFileContent("f", cf("three"), 30);

    assertNull(vcs.getByteContent("f", fileComparator(40)));
    assertEquals("three", new String(vcs.getByteContent("f", fileComparator(30))));
    assertEquals("two", new String(vcs.getByteContent("f", fileComparator(20))));
    assertEquals("one", new String(vcs.getByteContent("f", fileComparator(10))));
    assertNull(vcs.getByteContent("f", fileComparator(5)));
  }

  @Test
  public void testGettingMostRecentRevisionContent() {
    setCurrentTimestamp(10);
    vcs.createFile("f", cf("one"), -1, false);
    setCurrentTimestamp(20);
    vcs.changeFileContent("f", cf("two"), -1);

    FileRevisionTimestampComparator c = new FileRevisionTimestampComparator() {
      public boolean isSuitable(long fileTimestamp, long revisionTimestamp) {
        return revisionTimestamp < 100;
      }
    };
    assertEquals("two", new String(vcs.getByteContent("f", c)));
  }

  @Test
  public void testGettingContentForUnavailableContentIsNull() {
    setCurrentTimestamp(10);
    vcs.createFile("f", bigContentFactory(), -1, false);

    assertNull(vcs.getByteContent("f", revisionComparator(10)));
  }

  @Test
  public void testGettingContentIfPurgedIsNull() {
    setCurrentTimestamp(10);
    vcs.createFile("f", cf("one"), -1, false);
    setCurrentTimestamp(20);
    vcs.changeFileContent("f", cf("two"), -1);

    vcs.purgeObsolete(5);

    assertNull(vcs.getByteContent("f", revisionComparator(10)));
    assertEquals("two", new String(vcs.getByteContent("f", revisionComparator(20))));
  }

  private FileRevisionTimestampComparator revisionComparator(long revisionTimestamp) {
    return new TestTimestampComparator(-1, revisionTimestamp);
  }

  private FileRevisionTimestampComparator fileComparator(long fileTimestamp) {
    return new TestTimestampComparator(fileTimestamp, -1);
  }
}