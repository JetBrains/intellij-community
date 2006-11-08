package com.intellij.localvcs;

import org.junit.Test;

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
}
