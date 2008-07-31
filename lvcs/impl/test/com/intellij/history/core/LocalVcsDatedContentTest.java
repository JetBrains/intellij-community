package com.intellij.history.core;

import com.intellij.history.FileRevisionTimestampComparator;
import org.junit.Test;

public class LocalVcsDatedContentTest extends LocalVcsTestCase {
  LocalVcs vcs = new InMemoryLocalVcs();

  @Test
  public void testGettingContentByRevisionTimestamp() {
    vcs.createFile("f", cf("one"), 10, false);
    vcs.changeFileContent("f", cf("two"), 20);

    assertNull(vcs.getByteContent("f", comparator(5)));
    assertEquals("one", new String(vcs.getByteContent("f", comparator(10))));
    assertNull(vcs.getByteContent("f", comparator(15)));

    assertEquals("two", new String(vcs.getByteContent("f", comparator(20))));
    assertNull(vcs.getByteContent("f", comparator(100)));
  }

  @Test
  public void testGettingContentStampByFileTimestamp() {
    vcs.createFile("f", cf("one"), 10, false);
    vcs.changeFileContent("f", cf("two"), 20);
    vcs.changeFileContent("f", cf("three"), 30);

    assertNull(vcs.getByteContent("f", comparator((long)40)));
    assertEquals("three", new String(vcs.getByteContent("f", comparator((long)30))));
    assertEquals("two", new String(vcs.getByteContent("f", comparator((long)20))));
    assertEquals("one", new String(vcs.getByteContent("f", comparator((long)10))));
    assertNull(vcs.getByteContent("f", comparator((long)5)));
  }

  @Test
  public void testGettingFirstAvailableContentAfterPurge() {
    setCurrentTimestamp(10);
    vcs.createFile("f", cf("one"), 10, false);
    setCurrentTimestamp(20);
    vcs.changeFileContent("f", cf("two"), 20);
    setCurrentTimestamp(30);
    vcs.changeFileContent("f", cf("three"), 30);

    vcs.purgeObsoleteAndSave(5);

    assertNull(vcs.getByteContent("f", comparator(10)));
    assertEquals("two", new String(vcs.getByteContent("f", comparator(20))));
    assertEquals("three", new String(vcs.getByteContent("f", comparator(30))));
  }

  @Test
  public void testGettingContentDoesnConfuseSpecifiedEntryWithOthers() {
    vcs.createFile("f1", cf("one"), 10, false);
    vcs.createFile("f2", cf("one"), 10, false);

    vcs.beginChangeSet();
    vcs.changeFileContent("f1", cf("two"), 20);
    vcs.changeFileContent("f2", cf("three"), 20);
    vcs.endChangeSet(null);

    vcs.beginChangeSet();
    vcs.changeFileContent("f1", cf("four"), 30);
    vcs.changeFileContent("f2", cf("five"), 30);
    vcs.endChangeSet(null);

    assertEquals("two", new String(vcs.getByteContent("f1", comparator((long)20))));
    assertEquals("three", new String(vcs.getByteContent("f2", comparator((long)20))));
  }

  @Test
  public void testGettingMostRecentRevisionContent() {
    vcs.createFile("f", cf("one"), 10, false);
    vcs.changeFileContent("f", cf("two"), 20);

    FileRevisionTimestampComparator c = new FileRevisionTimestampComparator() {
      public boolean isSuitable(long revisionTimestamp) {
        return revisionTimestamp < 100;
      }
    };
    assertEquals("two", new String(vcs.getByteContent("f", c)));
  }

  @Test
  public void testGettingContentForUnavailableContentIsNull() {
    setCurrentTimestamp(10);
    vcs.createFile("f", bigContentFactory(), 10, false);

    assertNull(vcs.getByteContent("f", comparator(10)));
  }

  @Test
  public void testGettingContentByRevisionTimestampIfPurged() {
    setCurrentTimestamp(10);
    vcs.createFile("f", cf("one"), 10, false);
    setCurrentTimestamp(20);
    vcs.changeFileContent("f", cf("two"), 20);

    vcs.purgeObsoleteAndSave(0);

    assertNull(vcs.getByteContent("f", comparator(10)));
    assertEquals("two", new String(vcs.getByteContent("f", comparator(20))));
  }

  private FileRevisionTimestampComparator comparator(final long timestamp) {
    return new FileRevisionTimestampComparator() {
      public boolean isSuitable(long revisionTimestamp) {
        return revisionTimestamp == timestamp;
      }
    };
  }
}