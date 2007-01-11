package com.intellij.localvcs;

import org.junit.Before;
import org.junit.Test;
import org.junit.After;

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
  public void closeStorage(){
    if(s != null) s.close();
  }

  @Test
  public void testStoringEntries() {
    vcs.createFile("file", b("content"), 123L);
    vcs.apply();

    vcs.save();
    initVcs();

    Entry e = vcs.findEntry("file");
    assertNotNull(e);
    assertEquals(c("content"), e.getContent());
    assertEquals(123L, e.getTimestamp());
  }

  @Test
  public void testStoringChangeList() {
    vcs.createFile("file", b("content"), null);
    vcs.apply();
    vcs.changeFileContent("file", b("new content"), null);
    vcs.apply();

    vcs.save();
    initVcs();

    assertEquals(2, vcs.getLabelsFor("file").size());
  }

  @Test
  public void testStoringObjectsCounter() {
    vcs.createFile("file1", b("content1"), null);
    vcs.createFile("file2", b("content2"), null);
    vcs.apply();

    vcs.save();
    initVcs();

    vcs.createFile("file3", b("content3"), null);
    vcs.apply();

    Integer id2 = vcs.getEntry("file2").getId();
    Integer id3 = vcs.getEntry("file3").getId();

    assertTrue(id2 < id3);
  }

  @Test
  public void testDoesNotStoreUnappliedChanges() {
    vcs.createFile("file", b("content"), null);
    vcs.save();

    vcs.save();

    initVcs();
    assertTrue(vcs.isClean());
  }
}
