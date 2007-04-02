package com.intellij.localvcs;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class LocalVcsPurgingTest extends LocalVcsTestCase {
  TestLocalVcs vcs = new TestLocalVcs(new PurgeLoggingStorage());
  List<Content> purgedContent = new ArrayList<Content>();

  @Before
  public void setUp() {
    setCurrentTimestamp(10);
    vcs.createFile("file", b("one"), -1);

    setCurrentTimestamp(20);
    vcs.changeFileContent("file", b("two"), -1);

    setCurrentTimestamp(30);
    vcs.changeFileContent("file", b("three"), -1);

    setCurrentTimestamp(40);
    vcs.changeFileContent("file", b("four"), -1);
  }

  @Test
  public void testPurging() {
    assertEquals(4, vcs.getLabelsFor("file").size());

    vcs.purgeUpTo(35);

    List<Label> ll = vcs.getLabelsFor("file");
    assertEquals(2, ll.size());
    assertEquals(40L, ll.get(0).getTimestamp());
  }

  @Test
  public void testPurgingContents() {
    vcs.purgeUpTo(35);

    assertEquals(2, purgedContent.size());
    assertEquals(c("one"), purgedContent.get(0));
    assertEquals(c("two"), purgedContent.get(1));
  }

  @Test
  public void testDoesNotPurgeLongContentFromContentStorage() {
    vcs = new TestLocalVcs(new PurgeLoggingStorage());
    setCurrentTimestamp(20);

    vcs.createFile("file", new byte[IContentStorage.MAX_CONTENT_LENGTH + 1], -1);
    vcs.changeFileContent("file", b("new content"), -1);

    vcs.purgeUpTo(30);

    assertTrue(purgedContent.isEmpty());
  }

  @Test
  public void testPurgingOnSave() {
    vcs.setPurgingInterval(30);

    assertLabelsCountAfterPurgeOnSave(59, 3);
    assertLabelsCountAfterPurgeOnSave(61, 2);
  }

  private void assertLabelsCountAfterPurgeOnSave(long timestamp, int count) {
    setCurrentTimestamp(timestamp);
    vcs.save();
    assertEquals(count, vcs.getLabelsFor("file").size());
  }

  @Test
  public void testDefaultPurgingInterval() {
    LocalVcs vcs = new LocalVcs(new TestStorage());
    assertEquals(5 * 24 * 60 * 60 * 1000L, vcs.getPurgingInterval());
  }

  class PurgeLoggingStorage extends TestStorage {
    @Override
    public void purgeContent(Content c) {
      purgedContent.add(c);
    }
  }
}