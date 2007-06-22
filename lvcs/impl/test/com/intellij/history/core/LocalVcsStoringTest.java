package com.intellij.history.core;

import com.intellij.history.core.storage.Storage;
import com.intellij.history.core.tree.Entry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LocalVcsStoringTest extends TempDirTestCase {
  private LocalVcs vcs;
  private Storage s;

  @Before
  public void initVcs() {
    closeStorage();
    s = new Storage(tempDir);
    vcs = new TestLocalVcs(s);
  }

  @After
  public void closeStorage() {
    if (s != null) s.close();
  }

  @Test
  public void testStoringEntries() {
    vcs.createFile("file", cf("content"), 123L);

    vcs.save();
    initVcs();

    Entry e = vcs.findEntry("file");
    assertNotNull(e);
    assertEquals(c("content"), e.getContent());
    assertEquals(123L, e.getTimestamp());
  }

  @Test
  public void testStoringChangeList() {
    vcs.createFile("file", cf("content"), -1);
    vcs.changeFileContent("file", cf("new content"), -1);

    vcs.save();
    initVcs();

    assertEquals(2, vcs.getRevisionsFor("file").size());
  }

  @Test
  public void testStoringObjectsCounter() {
    vcs.createFile("file1", cf("content1"), -1);
    vcs.createFile("file2", cf("content2"), -1);

    vcs.save();
    initVcs();

    vcs.createFile("file3", cf("content3"), -1);

    int id2 = vcs.getEntry("file2").getId();
    int id3 = vcs.getEntry("file3").getId();

    assertTrue(id2 < id3);
  }

  @Test
  public void testSavingDuringChangeSet() {
    vcs.beginChangeSet();
    vcs.createFile("file", cf(""), -1);

    vcs.save();
    initVcs();
    assertTrue(vcs.hasEntry("file"));
    assertEquals(1, vcs.getRevisionsFor("file").size());
  }
}
