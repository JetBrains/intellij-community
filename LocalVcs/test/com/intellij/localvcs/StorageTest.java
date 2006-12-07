package com.intellij.localvcs;

import org.junit.Test;

import java.io.File;

public class StorageTest extends TempDirTestCase {
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
  public void testCreatingAbsentDirs() {
    File dir = new File(tempDir, "dir1/dir2/dir3");
    assertFalse(dir.exists());

    Storage s = new Storage(dir);
    s.storeCounter(1);
    assertTrue(dir.exists());
  }
}
