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
    assertEquals(4, vcs.getRevisionsFor("file").size());

    vcs.purgeUpTo(35);

    List<Revision> rr = vcs.getRevisionsFor("file");
    assertEquals(2, rr.size());
    assertEquals(40L, rr.get(0).getTimestamp());
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

    assertRevisionsCountAfterPurgeOnSave(59, 3);
    assertRevisionsCountAfterPurgeOnSave(61, 2);
  }

  private void assertRevisionsCountAfterPurgeOnSave(long timestamp, int count) {
    setCurrentTimestamp(timestamp);
    vcs.save();
    assertEquals(count, vcs.getRevisionsFor("file").size());
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