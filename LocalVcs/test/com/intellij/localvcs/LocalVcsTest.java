package com.intellij.localvcs;

import java.util.Collection;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class LocalVcsTest extends Assert {
  private LocalVcs myVcs;

  @Before
  public void setUp() {
    myVcs = new LocalVcs();
  }

  @Test
  public void testAddingFiles() {
    myVcs.addFile("file", "");
    assertFalse(myVcs.hasFile("file"));

    myVcs.commit();
    assertTrue(myVcs.hasFile("file"));
  }

  @Test
  public void testAddingTwoFiles() {
    myVcs.addFile("file1", "");
    myVcs.addFile("file2", "");
    myVcs.commit();

    assertTrue(myVcs.hasFile("file1"));
    assertTrue(myVcs.hasFile("file2"));

    assertFalse(myVcs.hasFile("unknown file"));
  }

  @Test
  public void testClearingAddedFiles() {
    assertTrue(myVcs.isClean());

    myVcs.addFile("file", "");
    assertFalse(myVcs.isClean());

    myVcs.commit();

    assertTrue(myVcs.isClean());
  }

  @Test
  public void testChangingContent() {
    myVcs.addFile("file", "content");
    myVcs.commit();

    myVcs.changeFile("file", "new content");
    myVcs.commit();

    assertEquals("new content", myVcs.getFileContent("file"));
  }

  @Test
  public void testContentOfUnknownFile() {
    assertNull(myVcs.getFileContent("unknown file"));
  }

  @Test
  public void testChangingOnlyOneFile() {
    myVcs.addFile("file1", "content1");
    myVcs.addFile("file2", "content2");
    myVcs.commit();

    myVcs.changeFile("file1", "new content");
    myVcs.commit();

    assertEquals("new content", myVcs.getFileContent("file1"));
    assertEquals("content2", myVcs.getFileContent("file2"));
  }

  @Test
  public void testDoesNotChangeContentBeforeCommit() {
    myVcs.addFile("file", "content");
    myVcs.commit();

    myVcs.changeFile("file", "new content");

    assertEquals("content", myVcs.getFileContent("file"));
  }

  @Test
  public void testClearingChangedFiles() {
    myVcs.addFile("file", "content");
    myVcs.commit();

    assertTrue(myVcs.isClean());

    myVcs.changeFile("file", "new content");
    assertFalse(myVcs.isClean());

    myVcs.commit();
    assertTrue(myVcs.isClean());
  }

  @Test
  public void testRenaming() {
    myVcs.addFile("file", "content");
    myVcs.commit();

    myVcs.renameFile("file", "new file");
    myVcs.commit();

    assertFalse(myVcs.hasFile("file"));
    assertTrue(myVcs.hasFile("new file"));

    assertEquals("content", myVcs.getFileContent("new file"));
  }

  @Test
  public void testDeleting() {
    myVcs.addFile("file", "content");
    myVcs.commit();

    myVcs.deleteFile("file");
    assertEquals("content", myVcs.getFileContent("file"));

    myVcs.commit();
    assertFalse(myVcs.hasFile("file"));
    assertNull(myVcs.getFileContent("file"));
  }

  @Test
  public void testDeletingOnlyOneFile() {
    myVcs.addFile("file1", "");
    myVcs.addFile("file2", "");
    myVcs.commit();

    myVcs.deleteFile("file2");
    myVcs.commit();

    assertTrue(myVcs.hasFile("file1"));
    assertFalse(myVcs.hasFile("file2"));
  }

  @Test
  public void testClearingDeletedFiles() {
    myVcs.addFile("file", "content");
    myVcs.commit();

    assertTrue(myVcs.isClean());

    myVcs.deleteFile("file");
    assertFalse(myVcs.isClean());

    myVcs.commit();
    assertTrue(myVcs.isClean());
  }

  @Test
  public void testAddingAndDeletingSameFileBeforeCommit() {
    myVcs.addFile("file", "");
    myVcs.deleteFile("file");
    myVcs.commit();

    assertFalse(myVcs.hasFile("file"));
  }

  @Test
  public void testDeletingAndAddingSameFileBeforeCommit() {
    myVcs.addFile("file", "");
    myVcs.commit();

    myVcs.deleteFile("file");
    myVcs.addFile("file", "");
    myVcs.commit();

    assertTrue(myVcs.hasFile("file"));
  }

  @Test
  public void testAddingAndChangingSameFileBeforeCommit() {
    myVcs.addFile("file", "content");
    myVcs.changeFile("file", "new content");
    myVcs.commit();

    assertEquals("new content", myVcs.getFileContent("file"));
  }

  @Test
  public void testKeepingOldVersions() {
    myVcs.addFile("file", "content");
    myVcs.commit();

    myVcs.changeFile("file", "new content");
    myVcs.commit();

    assertEquals(new String[]{"content", "new content" },
                 myVcs.getFileContents("file"));
  }

  @Test
  public void testDoesNotKeepUncommittedChanges() {
    myVcs.addFile("file", "content");
    myVcs.commit();

    myVcs.changeFile("file", "new content");

    assertEquals(new String[]{"content" }, myVcs.getFileContents("file"));
  }

  @SuppressWarnings("unchecked")
  private void assertEquals(Object[] expected, Collection actual) {
    assertEquals(expected, actual.toArray(new Object[0]));
  }
}
