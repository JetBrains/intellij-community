package com.intellij.history.integration;

import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.openapi.vfs.VirtualFile;
import static org.easymock.classextension.EasyMock.*;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class EventDispatcherListeningTest extends EventDispatcherTestCase {
  TestFileFilter filter = new TestFileFilter();

  @Override
  @Before
  public void setUp() {
    super.setUp();
    gateway.setFileFilter(filter);
  }

  @Test
  public void testCreatingFiles() {
    VirtualFile f = new TestVirtualFile("file", "content", 123L);
    fireCreated(f);

    Entry e = vcs.findEntry("file");
    assertNotNull(e);

    assertFalse(e.isDirectory());

    assertEquals(c("content"), e.getContent());
    assertEquals(123L, e.getTimestamp());
  }

  @Test
  public void testTakingPhysicalFileContentOnCreation() {
    configureToReturnPhysicalContent("physical");

    VirtualFile f = new TestVirtualFile("f", "memory", -1);
    fireCreated(f);

    assertEquals(c("physical"), vcs.getEntry("f").getContent());
  }

  @Test
  public void testCreatingDirectories() {
    VirtualFile f = new TestVirtualFile("dir");
    fireCreated(f);

    Entry e = vcs.findEntry("dir");
    assertNotNull(e);
    assertTrue(e.isDirectory());
  }

  @Test
  public void testCreatingDirectoriesWithChildren() {
    TestVirtualFile dir1 = new TestVirtualFile("dir1");
    TestVirtualFile dir2 = new TestVirtualFile("dir2");
    TestVirtualFile file = new TestVirtualFile("file", "", -1);

    dir1.addChild(dir2);
    dir2.addChild(file);
    fireCreated(dir1);

    assertTrue(vcs.hasEntry("dir1"));
    assertTrue(vcs.hasEntry("dir1/dir2"));
    assertTrue(vcs.hasEntry("dir1/dir2/file"));
  }

  @Test
  public void testCreationOfDirectoryWithChildrenIsThreatedAsOneChange() {
    TestVirtualFile dir = new TestVirtualFile("dir");
    dir.addChild(new TestVirtualFile("one", null, -1));
    dir.addChild(new TestVirtualFile("two", null, -1));
    fireCreated(dir);

    assertTrue(vcs.hasEntry("dir"));
    assertTrue(vcs.hasEntry("dir/one"));
    assertTrue(vcs.hasEntry("dir/two"));

    assertEquals(1, vcs.getRevisionsFor("dir").size());
  }

  @Test
  public void testIgnoringCreationOfAlreadyExistedFiles() {
    vcs.createFile("f", null, -1);
    fireCreated(new TestVirtualFile("f"));

    assertEquals(1, vcs.getChangeList().getChanges().size());
  }

  @Test
  public void testChangingFileContent() {
    vcs.createFile("file", cf("old content"), -1);

    VirtualFile f = new TestVirtualFile("file", "new content", 505L);
    fireContentChanged(f);

    Entry e = vcs.getEntry("file");
    assertEquals(c("new content"), e.getContent());
    assertEquals(505L, e.getTimestamp());
  }

  @Test
  public void testTakingPhysicalFileContentOnContentChange() {
    configureToReturnPhysicalContent("physical");

    vcs.createFile("f", cf("content"), -1);

    VirtualFile f = new TestVirtualFile("f", "memory", -1);
    fireContentChanged(f);

    assertEquals(c("physical"), vcs.getEntry("f").getContent());
  }

  @Test
  public void testRenaming() {
    vcs.createFile("old name", cf("old content"), -1);

    VirtualFile f = new TestVirtualFile("new name", null, -1);
    fireRenamed(f, "old name");

    Entry e = vcs.findEntry("new name");
    assertNotNull(e);
    assertEquals(c("old content"), e.getContent());

    assertFalse(vcs.hasEntry("old name"));
  }

  @Test
  public void testRestoringFileAfterDeletion() {
    vcs.createFile("f", cf("one"), -1);
    vcs.changeFileContent("f", cf("two"), -1);
    Entry e = vcs.getEntry("f");
    vcs.delete("f");

    fireCreated(new TestVirtualFile("f", "two_restored", -1), e);

    List<Revision> rr = vcs.getRevisionsFor("f");
    assertEquals(3, rr.size());
    assertEquals(c("two_restored"), rr.get(0).getEntry().getContent());
    assertEquals(c("two"), rr.get(1).getEntry().getContent());
    assertEquals(c("one"), rr.get(2).getEntry().getContent());
  }

  @Test
  public void testRestoringDirectoryAfterDeletion() {
    vcs.createDirectory("dir");
    vcs.createFile("dir/f", cf("one"), -1);
    vcs.changeFileContent("dir/f", cf("two"), -1);
    Entry dir = vcs.getEntry("dir");
    Entry f = vcs.getEntry("dir/f");
    vcs.delete("dir");

    fireCreated(new TestVirtualFile("dir"), dir);
    fireCreated(new TestVirtualFile("dir/f", "two_restored", -1), f);

    vcs.changeFileContent("dir/f", cf("three"), -1);

    List<Revision> rr = vcs.getRevisionsFor("dir");
    assertEquals(6, rr.size());
    assertEquals(c("three"), rr.get(0).getEntry().findChild("f").getContent());
    assertEquals(c("two_restored"), rr.get(1).getEntry().findChild("f").getContent());
    assertNull(rr.get(2).getEntry().findChild("f"));
    assertEquals(c("two"), rr.get(3).getEntry().findChild("f").getContent());
    assertEquals(c("one"), rr.get(4).getEntry().findChild("f").getContent());
    assertNull(rr.get(5).getEntry().findChild("f"));
  }

  @Test
  public void testDoesNotRestoreIfEventNotWithEntryRequestor() {
    fireCreated(new TestVirtualFile("f", "", -1), new Object());
    assertTrue(vcs.hasEntry("f"));
  }

  @Test
  public void testDoNothingOnAnotherPropertyChanges() throws Exception {
    // shouldn't throw any exception here to make test pass
    VirtualFile f = new TestVirtualFile("", null, -1);
    firePropertyChanged(f, "another property", null);
  }

  @Test
  public void testMoving() {
    vcs.createDirectory("dir1");
    vcs.createDirectory("dir2");
    vcs.createFile("dir1/file", cf("content"), -1);

    TestVirtualFile oldParent = new TestVirtualFile("dir1");
    TestVirtualFile newParent = new TestVirtualFile("dir2");
    TestVirtualFile f = new TestVirtualFile("file", null, -1);
    newParent.addChild(f);
    fireMoved(f, oldParent, newParent);

    assertFalse(vcs.hasEntry("dir1/file"));

    Entry e = vcs.findEntry("dir2/file");

    assertNotNull(e);
    assertEquals(c("content"), e.getContent());
  }

  @Test
  public void testMovingFilteredFile() {
    vcs.createDirectory("dir1");
    vcs.createDirectory("dir2");

    TestVirtualFile oldParent = new TestVirtualFile("dir1");
    TestVirtualFile newParent = new TestVirtualFile("dir2");
    TestVirtualFile f = new TestVirtualFile("file", null, -1);
    newParent.addChild(f);

    filter.setNotAllowedFiles(f);

    fireMoved(f, oldParent, newParent);
    assertFalse(vcs.hasEntry("dir1/file"));
  }

  @Test
  public void testMovingFromOutsideOfTheContentRoots() {
    vcs.createDirectory("myRoot");

    TestVirtualFile f = new TestVirtualFile("file", "content", -1);
    TestVirtualFile oldParent = new TestVirtualFile("anotherRoot");
    TestVirtualFile newParent = new TestVirtualFile("myRoot");
    newParent.addChild(f);

    filter.setFilesNotUnderContentRoot(oldParent);

    fireMoved(f, oldParent, newParent);

    Entry e = vcs.findEntry("myRoot/file");
    assertNotNull(e);
    assertEquals(c("content"), e.getContent());
  }

  @Test
  public void testMovingFilteredFileFromOutsideOfTheContentRoots() {
    vcs.createDirectory("myRoot");

    TestVirtualFile f = new TestVirtualFile("file", "content", -1);
    TestVirtualFile oldParent = new TestVirtualFile("anotherRoot");
    TestVirtualFile newParent = new TestVirtualFile("myRoot");
    newParent.addChild(f);

    filter.setFilesNotUnderContentRoot(oldParent);
    filter.setNotAllowedFiles(f);

    fireMoved(f, oldParent, newParent);

    assertFalse(vcs.hasEntry("myRoot/file"));
  }

  @Test
  public void testMovingToOutsideOfTheContentRoots() {
    vcs.createDirectory("myRoot");
    vcs.createFile("myRoot/file", null, -1);

    TestVirtualFile f = new TestVirtualFile("file", "content", -1);
    TestVirtualFile oldParent = new TestVirtualFile("myRoot");
    TestVirtualFile newParent = new TestVirtualFile("anotherRoot");
    newParent.addChild(f);

    filter.setFilesNotUnderContentRoot(newParent);

    fireMoved(f, oldParent, newParent);

    assertFalse(vcs.hasEntry("myRoot/file"));
    assertFalse(vcs.hasEntry("anotherRoot/file"));
  }

  @Test
  public void testMovingFilteredFileToOutsideOfTheContentRoots() {
    vcs.createDirectory("myRoot");

    TestVirtualFile f = new TestVirtualFile("file", "content", -1);
    TestVirtualFile oldParent = new TestVirtualFile("myRoot");
    TestVirtualFile newParent = new TestVirtualFile("anotherRoot");
    newParent.addChild(f);

    filter.setFilesNotUnderContentRoot(newParent);
    filter.setNotAllowedFiles(f);

    fireMoved(f, oldParent, newParent);
    assertFalse(vcs.hasEntry("myRoot/file"));
  }

  @Test
  public void testMovingAroundOutsideContentRoots() {
    TestVirtualFile f = new TestVirtualFile("file", "content", -1);
    TestVirtualFile oldParent = new TestVirtualFile("root1");
    TestVirtualFile newParent = new TestVirtualFile("root2");
    newParent.addChild(f);

    filter.setFilesNotUnderContentRoot(oldParent, newParent);

    fireMoved(f, oldParent, newParent);

    assertFalse(vcs.hasEntry("root1/file"));
    assertFalse(vcs.hasEntry("root2/file"));
  }

  @Test
  public void testDeletionFromDirectory() {
    vcs.createDirectory("dir");
    vcs.createFile("file", null, -1);

    TestVirtualFile dir = new TestVirtualFile("dir", null, -1);
    TestVirtualFile f = new TestVirtualFile("file", null, -1);
    fireDeletion(f, dir);

    assertTrue(vcs.hasEntry("dir"));
    assertFalse(vcs.hasEntry("dir/file"));
  }

  @Test
  public void testDeletionWithoutParent() {
    vcs.createFile("file", null, -1);

    fireDeletion(new TestVirtualFile("file", null, -1));

    assertFalse(vcs.hasEntry("file"));
  }

  @Test
  public void testDeletionOfFileThanIsNotUnderVcsDoesNotThrowException() {
    VirtualFile f = new TestVirtualFile("non-existent", null, -1);
    fireDeletion(f); // should'n throw
  }

  @Test
  public void testFilteringFiles() {
    vcs = createMock(LocalVcs.class);
    replay(vcs);

    setUp();

    VirtualFile f = new TestVirtualFile("file", null, -1);
    filter.setFilesNotUnderContentRoot(f);

    fireCreated(f);
    fireContentChanged(f);
    fireMoved(f, f, f);

    verify(vcs);
  }

  private void configureToReturnPhysicalContent(String c) {
    gateway.setPhysicalContent(c);
  }
}
