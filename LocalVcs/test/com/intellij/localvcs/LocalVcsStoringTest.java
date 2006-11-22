package com.intellij.localvcs;

import org.junit.Before;
import org.junit.Test;
import org.junit.Ignore;

public class LocalVcsStoringTest extends TempDirTestCase {
  private LocalVcs vcs;

  @Before
  public void setUp() {
    vcs = createVcs();
  }

  private LocalVcs createVcs() {
    return new LocalVcs(new Storage(tempDir));
  }

  @Test
  public void testCleanStorage() {
    Storage s = new Storage(tempDir);

    ChangeList changeList = s.loadChangeList();
    RootEntry entry = s.loadRootEntry();
    Integer counter = s.loadCounter();

    assertTrue(changeList.getChangeSets().isEmpty());
    assertTrue(entry.getChildren().isEmpty());
    assertEquals(0, counter);
  }

  @Test
  public void testStoringEntries() {
    vcs.createFile("file", "content", null);
    vcs.apply();

    vcs.store();
    LocalVcs result = createVcs();

    assertTrue(result.hasEntry("file"));
  }

  @Ignore
  @Test
  public void testStoringChangeList() {
    vcs.createFile("file", "content", null);
    vcs.apply();
    vcs.changeFileContent("file", "new content", null);
    vcs.apply();

    vcs.store();
    LocalVcs result = createVcs();

    assertEquals(2, result.getLabelsFor("file").size());
  }

  @Test
  public void testStoringObjectsCounter() {
    vcs.createFile("file1", "content1", null);
    vcs.createFile("file2", "content2", null);
    vcs.apply();

    vcs.store();
    LocalVcs result = createVcs();

    result.createFile("file3", "content3", null);
    result.apply();

    Integer id2 = result.getEntry("file2").getId();
    Integer id3 = result.getEntry("file3").getId();

    assertTrue(id2 < id3);
  }

  @Test
  public void testDoesNotStoreUnappliedChanges() {
    vcs.createFile("file", "content", null);
    vcs.store();

    vcs.store();
    LocalVcs result = createVcs();
    assertTrue(result.isClean());
  }
}
