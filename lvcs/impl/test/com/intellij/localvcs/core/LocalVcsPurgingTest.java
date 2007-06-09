package com.intellij.localvcs.core;

import com.intellij.localvcs.core.revisions.Revision;
import com.intellij.localvcs.core.storage.Content;
import com.intellij.localvcs.core.storage.StoredContent;
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
    vcs.createFile("file", cf("one"), -1);

    setCurrentTimestamp(20);
    vcs.changeFileContent("file", cf("two"), -1);

    setCurrentTimestamp(30);
    vcs.changeFileContent("file", cf("three"), -1);

    setCurrentTimestamp(40);
    vcs.changeFileContent("file", cf("four"), -1);
  }

  @Test
  public void testPurging() {
    assertEquals(4, vcs.getRevisionsFor("file").size());

    vcs.purgeObsolete(5);

    List<Revision> rr = vcs.getRevisionsFor("file");
    assertEquals(2, rr.size());
    assertEquals(40L, rr.get(0).getTimestamp());
  }

  @Test
  public void testPurgingContents() {
    vcs.purgeObsolete(5);

    assertEquals(2, purgedContent.size());
    assertEquals(c("one"), purgedContent.get(0));
    assertEquals(c("two"), purgedContent.get(1));
  }

  @Test
  public void testDoesNotPurgeLongContentFromContentStorage() {
    vcs = new TestLocalVcs(new PurgeLoggingStorage());
    setCurrentTimestamp(10);
    vcs.createFile("file", bigContentFactory(), -1);

    setCurrentTimestamp(20);
    vcs.changeFileContent("file", cf("one"), -1);

    setCurrentTimestamp(30);
    vcs.changeFileContent("file", cf("twoo"), -1);

    vcs.purgeObsolete(5);

    assertTrue(purgedContent.isEmpty());
  }

  @Test
  public void testPurgingOnSave() {
    vcs.setPurgingPeriod(5);

    vcs.save();
    assertEquals(2, vcs.getRevisionsFor("file").size());
  }

  class PurgeLoggingStorage extends TestStorage {
    @Override
    public void purgeContent(StoredContent c) {
      purgedContent.add(c);
    }
  }
}