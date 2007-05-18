package com.intellij.localvcs.core.tree;

import com.intellij.localvcs.core.LocalVcsTestCase;
import org.junit.Test;

public class RootEntryFilesTest extends LocalVcsTestCase {
  private RootEntry root = new RootEntry();

  @Test
  public void testCreatingFiles() {
    assertFalse(root.hasEntry("file"));

    root.createFile(-1, "file", c("content"), 42);
    assertTrue(root.hasEntry("file"));

    Entry e = root.getEntry("file");

    assertEquals(FileEntry.class, e.getClass());
    assertEquals(c("content"), e.getContent());
    assertEquals(42L, e.getTimestamp());
  }

  @Test
  public void testReturningCreatedFileEntry() {
    Entry e = root.createFile(-1, "f", null, -1);
    assertSame(e, root.getEntry("f"));
  }

  @Test
  public void testCreatingTwoFiles() {
    root.createFile(-1, "file1", null, -1);
    root.createFile(-1, "file2", null, -1);

    assertTrue(root.hasEntry("file1"));
    assertTrue(root.hasEntry("file2"));

    assertFalse(root.hasEntry("unknown file"));
  }

  @Test
  public void testChangingFileContent() {
    root.createFile(1, "file", c("content"), -1);
    root.changeFileContent("file", c("new content"), 77);

    Entry e = root.getEntry("file");

    assertEquals(c("new content"), e.getContent());
    assertEquals(77L, e.getTimestamp());
  }

  @Test
  public void testChangingFileContentAffectsOnlyOneFile() {
    root.createFile(1, "file1", c("content1"), -1);
    root.createFile(2, "file2", c("content2"), -1);

    root.changeFileContent("file1", c("new content"), -1);

    assertEquals(c("new content"), root.getEntry("file1").getContent());
    assertEquals(c("content2"), root.getEntry("file2").getContent());
  }

  @Test
  public void testChangingContentOfAnNonExistingFileThrowsException() {
    try {
      root.changeFileContent("unknown file", c("content"), -1);
      fail();
    }
    catch (RuntimeException e) {
    }
  }

  @Test
  public void testRenamingFiles() {
    root.createFile(1, "file", c("content"), -1);

    root.rename("file", "new file");

    assertFalse(root.hasEntry("file"));

    Entry e = root.findEntry("new file");

    assertNotNull(e);
    assertEquals(c("content"), e.getContent());
  }

  @Test
  public void testRenamingOfAnNonExistingFileThrowsException() {
    try {
      root.rename("unknown file", "new file");
      fail();
    }
    catch (RuntimeException e) {
    }
  }

  @Test
  public void testRenamingFileToAnExistingFileNameThrowsException() {
    root.createFile(1, "file1", null, -1);
    root.createFile(2, "file2", null, -1);

    try {
      root.rename("file1", "file2");
      fail();
    }
    catch (RuntimeException e) {
    }
  }

  @Test
  public void testRenamingFileToSameNameDoesNothing() {
    root.createFile(1, "file", c("content"), -1);

    root.rename("file", "file");

    assertTrue(root.hasEntry("file"));
    assertEquals(c("content"), root.getEntry("file").getContent());
  }

  @Test
  public void testDeletingFiles() {
    root.createFile(1, "file", null, -1);

    root.delete("file");

    assertFalse(root.hasEntry("file"));
  }

  @Test
  public void testDeletingOnlyOneFile() {
    root.createFile(1, "file1", null, -1);
    root.createFile(2, "file2", null, -1);

    root.delete("file2");

    assertTrue(root.hasEntry("file1"));
    assertFalse(root.hasEntry("file2"));
  }

  @Test
  public void testDeletingOfAnUnknownEntryThrowsException() {
    try {
      root.delete("unknown file");
      fail();
    }
    catch (RuntimeException e) {
    }
  }
}
