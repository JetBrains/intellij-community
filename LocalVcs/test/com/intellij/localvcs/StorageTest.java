package com.intellij.localvcs;

import org.junit.After;
import org.junit.Test;

import java.io.File;

public class StorageTest extends TempDirTestCase {
  private Storage s;

  @After
  public void tearDown() {
    if (s != null) s.close();
  }

  @Test
  public void testCleanStorage() {
    s = new Storage(tempDir);

    ChangeList changeList = s.loadChangeList();
    RootEntry entry = s.loadRootEntry();
    Integer counter = s.loadCounter();

    assertTrue(changeList.getChangeSets().isEmpty());
    assertTrue(entry.getChildren().isEmpty());
    assertEquals(0, counter);
  }

  @Test
  public void testCreatingAbsentDirs() {
    File dir = new File(tempDir, "dir1/dir2/dir3");
    assertFalse(dir.exists());

    s = new Storage(dir);
    s.storeCounter(1);
    assertTrue(dir.exists());
  }

  @Test
  public void testStoringContent() {
    s = new Storage(tempDir);

    Content c = s.createContent(new byte[]{1, 2, 3});
    assertEquals(new byte[]{1, 2, 3}, c.getData());
  }
}
