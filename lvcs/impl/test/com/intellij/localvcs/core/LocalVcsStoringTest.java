package com.intellij.localvcs.core;

import com.intellij.localvcs.core.storage.Storage;
import com.intellij.localvcs.core.tree.Entry;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class LocalVcsStoringTest extends TempDirTestCase {
  private LocalVcs vcs;
  private Storage s;

  @Before
  public void initVcs() {
    closeStorage();
    s = new Storage(tempDir);
    vcs = new LocalVcs(s);
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
  @Ignore("unignore after moving save postponding to service level")
  public void testSavingDuringChangeSetThrowsException() {
    vcs.beginChangeSet();
    try {
      vcs.save();
      fail();
    }
    catch (IllegalStateException e) {
    }
  }

  @Test
  public void testPospondingSaveUntilChangeSetFinished() {
    vcs.beginChangeSet();
    vcs.createFile("file", cf(""), -1);

    vcs.save();
    assertFalse(hasSavedEntry("file"));

    vcs.endChangeSet(null);
    assertTrue(hasSavedEntry("file"));
  }

  @Test
  public void testDoesNotSaveAfterNextChangeSetIfSaveWasPospondedDuringPriorOne() {
    vcs.beginChangeSet();
    vcs.createFile("one", cf(""), -1);
    vcs.save();
    vcs.endChangeSet(null);

    assertTrue(hasSavedEntry("one"));

    vcs.beginChangeSet();
    vcs.createFile("two", cf(""), -1);
    vcs.endChangeSet(null);

    assertTrue(hasSavedEntry("one"));
    assertFalse(hasSavedEntry("two"));
  }

  private boolean hasSavedEntry(String path) {
    Storage s = new Storage(tempDir);
    LocalVcs vcs = new LocalVcs(s);
    boolean result = vcs.hasEntry(path);
    s.close();
    return result;
  }
}
