package com.intellij.localvcs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

    assertRevisionContent("new content", myVcs.getFileRevision("file"));
  }

  @Test
  public void testContentOfUnknownFile() {
    assertNull(myVcs.getFileRevision("unknown file"));
  }

  @Test
  public void testChangingOnlyOneFile() {
    myVcs.addFile("file1", "content1");
    myVcs.addFile("file2", "content2");
    myVcs.commit();

    myVcs.changeFile("file1", "new content");
    myVcs.commit();

    assertRevisionContent("new content", myVcs.getFileRevision("file1"));
    assertRevisionContent("content2", myVcs.getFileRevision("file2"));
  }

  @Test
  public void testDoesNotChangeContentBeforeCommit() {
    myVcs.addFile("file", "content");
    myVcs.commit();

    myVcs.changeFile("file", "new content");

    assertRevisionContent("content", myVcs.getFileRevision("file"));
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

    assertRevisionContent("content", myVcs.getFileRevision("new file"));
  }

  @Test
  public void testDeleting() {
    myVcs.addFile("file", "content");
    myVcs.commit();

    myVcs.deleteFile("file");
    assertRevisionContent("content", myVcs.getFileRevision("file"));

    myVcs.commit();
    assertFalse(myVcs.hasFile("file"));
    assertNull(myVcs.getFileRevision("file"));
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

    assertRevisionContent("new content", myVcs.getFileRevision("file"));
  }

  @Test
  public void testKeepingOldVersions() {
    myVcs.addFile("file", "content");
    myVcs.commit();

    myVcs.changeFile("file", "new content");
    myVcs.commit();

    assertRevisionsContent(new String[]{"content", "new content" },
                           myVcs.getFileRevisions("file"));
  }

  @Test
  public void testDoesNotKeepUncommittedChanges() {
    myVcs.addFile("file", "content");
    myVcs.commit();

    myVcs.changeFile("file", "new content");

    assertRevisionsContent(new String[]{"content" },
                           myVcs.getFileRevisions("file"));
  }

  private void assertRevisionContent(String expectedContent,
                                     LocalVcs.Revision actualRevision) {
    assertEquals(expectedContent, actualRevision.getContent());
  }

  private void assertRevisionsContent(String[] expectedContents,
                                      Collection<LocalVcs.Revision> actualRevisions) {
    List<String> actualContents = new ArrayList<String>();
    for (LocalVcs.Revision rev : actualRevisions) {
      actualContents.add(rev.getContent());
    }
    assertEquals(expectedContents, actualContents.toArray(new Object[0]));
  }
}
