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
    VirtualFile f = testFile("file", "content", 123L, true);
    fireCreated(f);

    Entry e = vcs.findEntry("file");
    assertNotNull(e);

    assertFalse(e.isDirectory());

    assertEquals(c("content"), e.getContent());
    assertEquals(123L, e.getTimestamp());
    assertTrue(e.isReadOnly());
  }

  @Test
  public void testCreatingDirectories() {
    VirtualFile f = testDir("dir");
    fireCreated(f);

    Entry e = vcs.findEntry("dir");
    assertNotNull(e);
    assertTrue(e.isDirectory());
  }

  @Test
  public void testCreatingDirectoriesWithChildren() {
    TestVirtualFile dir1 = testDir("dir1");
    TestVirtualFile dir2 = testDir("dir2");
    TestVirtualFile file = testFile("file");

    dir1.addChild(dir2);
    dir2.addChild(file);
    fireCreated(dir1);

    assertTrue(vcs.hasEntry("dir1"));
    assertTrue(vcs.hasEntry("dir1/dir2"));
    assertTrue(vcs.hasEntry("dir1/dir2/file"));
  }

  @Test
  public void testIgnoringUnversioningFilesAndDirectories() {
    TestVirtualFile dir = testDir("dir");
    TestVirtualFile f = testFile("f");

    filter.setNotAllowedFiles(dir, f);

    fireCreated(dir);
    fireCreated(f);

    assertFalse(vcs.hasEntry("dir"));
    assertFalse(vcs.hasEntry("f"));
  }

  @Test
  public void testIgnoringCreationOfUnversionedChildrenOfVersionedDirectory() {
    TestVirtualFile dir = testDir("dir");
    TestVirtualFile subDir = testDir("subDir");
    TestVirtualFile file = testFile("file");
    TestVirtualFile subFile = testFile("subFile");

    dir.addChild(subDir);
    dir.addChild(file);
    subDir.addChild(subFile);

    filter.setNotAllowedFiles(subDir, file);

    fireCreated(dir);

    assertTrue(vcs.hasEntry("dir"));
    assertFalse(vcs.hasEntry("dir/subDir"));
    assertFalse(vcs.hasEntry("dir/subDir/subFile"));
    assertFalse(vcs.hasEntry("dir/file"));
  }

  @Test
  public void testCreationOfDirectoryWithChildrenIsThreatedAsOneChange() {
    TestVirtualFile dir = testDir("dir");
    dir.addChild(testFile("one"));
    dir.addChild(testFile("two"));
    fireCreated(dir);

    assertTrue(vcs.hasEntry("dir"));
    assertTrue(vcs.hasEntry("dir/one"));
    assertTrue(vcs.hasEntry("dir/two"));

    assertEquals(1, vcs.getRevisionsFor("dir").size());
  }

  @Test
  public void testIgnoringCreationOfAlreadyExistedFiles() {
    long timestamp = -1;
    vcs.createFile("f", null, timestamp, false);
    fireCreated(testFile("f"));

    assertEquals(1, vcs.getChangeList().getChanges().size());
  }

  @Test
  public void testChangingFileContent() {
    long timestamp = -1;
    vcs.createFile("file", cf("old content"), timestamp, false);

    VirtualFile f = testFile("file", "new content", 505L);
    fireContentChanged(f);

    Entry e = vcs.getEntry("file");
    assertEquals(c("new content"), e.getContent());
    assertEquals(505L, e.getTimestamp());
  }

  @Test
  public void testRenaming() {
    long timestamp = -1;
    vcs.createFile("old name", cf("old content"), timestamp, false);

    VirtualFile f = testFile("new name");
    fireRenamed(f, "old name");

    Entry e = vcs.findEntry("new name");
    assertNotNull(e);
    assertEquals(c("old content"), e.getContent());

    assertFalse(vcs.hasEntry("old name"));
  }
  
  @Test
  public void testDoNothingOnAnotherPropertyChanges() throws Exception {
    // shouldn't throw any exception here to make test pass
    firePropertyChanged(testFile(""), "another property", null);
  }

  @Test
  public void testChangingROStatus() {
    vcs.createFile("f", null, -1, false);
    assertFalse(vcs.getEntry("f").isReadOnly());

    fireROStatusChanged(testFile("f", null, -1, true));
    assertTrue(vcs.getEntry("f").isReadOnly());
  }

  @Test
  public void testIgnoringChangeOfROStatusOfDirectories() {
    vcs.createDirectory("dir");
    fireROStatusChanged(testDir("dir")); // shouldn't throw
  }
  
  @Test
  public void testIgnoringChangeOfROStatusOfUnversionedFiles() {
    TestVirtualFile f = testFile("f", null, -1, true);
    filter.setNotAllowedFiles(f);

    fireROStatusChanged(f); // shouldn't throw
  }

  @Test
  public void testRestoringFileAfterDeletion() {
    vcs.createFile("f", cf("one"), -1, false);
    vcs.changeFileContent("f", cf("two"), -1);
    Entry e = vcs.getEntry("f");
    vcs.delete("f");

    fireCreated(testFile("f", "two_restored"), e);

    List<Revision> rr = vcs.getRevisionsFor("f");
    assertEquals(3, rr.size());
    assertEquals(c("two_restored"), rr.get(0).getEntry().getContent());
    assertEquals(c("two"), rr.get(1).getEntry().getContent());
    assertEquals(c("one"), rr.get(2).getEntry().getContent());
  }
  
  @Test
  public void testRestoringFileROStatus() {
    vcs.createFile("f", cf(""), -1, true);

    Entry e = vcs.getEntry("f");
    vcs.delete("f");

    fireCreated(testFile("f", ""), e);

    assertTrue(vcs.getEntry("f").isReadOnly());
  }

  @Test
  public void testRestoringDirectoryAfterDeletion() {
    vcs.createDirectory("dir");
    long timestamp = -1;
    vcs.createFile("dir/f", cf("one"), timestamp, false);
    vcs.changeFileContent("dir/f", cf("two"), -1);
    Entry dir = vcs.getEntry("dir");
    Entry f = vcs.getEntry("dir/f");
    vcs.delete("dir");

    fireCreated(testDir("dir"), dir);
    fireCreated(testFile("dir/f", "two_restored"), f);

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
    fireCreated(testFile("f"), new Object());
    assertTrue(vcs.hasEntry("f"));
  }

  @Test
  public void testMoving() {
    vcs.createDirectory("dir1");
    vcs.createDirectory("dir2");
    long timestamp = -1;
    vcs.createFile("dir1/file", cf("content"), timestamp, false);

    TestVirtualFile oldParent = testDir("dir1");
    TestVirtualFile newParent = testDir("dir2");
    TestVirtualFile f = testFile("file");
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

    TestVirtualFile oldParent = testDir("dir1");
    TestVirtualFile newParent = testDir("dir2");
    TestVirtualFile f = testFile("file");
    newParent.addChild(f);

    filter.setNotAllowedFiles(f);

    fireMoved(f, oldParent, newParent);
    assertFalse(vcs.hasEntry("dir1/file"));
  }

  @Test
  public void testMovingFromOutsideOfTheContentRoots() {
    vcs.createDirectory("myRoot");

    TestVirtualFile f = testFile("file", "content");
    TestVirtualFile oldParent = testDir("anotherRoot");
    TestVirtualFile newParent = testDir("myRoot");
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

    TestVirtualFile f = testFile("file", "content");
    TestVirtualFile oldParent = testDir("anotherRoot");
    TestVirtualFile newParent = testDir("myRoot");
    newParent.addChild(f);

    filter.setFilesNotUnderContentRoot(oldParent);
    filter.setNotAllowedFiles(f);

    fireMoved(f, oldParent, newParent);

    assertFalse(vcs.hasEntry("myRoot/file"));
  }

  @Test
  public void testMovingToOutsideOfTheContentRoots() {
    vcs.createDirectory("myRoot");
    long timestamp = -1;
    vcs.createFile("myRoot/file", null, timestamp, false);

    TestVirtualFile f = testFile("file", "content");
    TestVirtualFile oldParent = testDir("myRoot");
    TestVirtualFile newParent = testDir("anotherRoot");
    newParent.addChild(f);

    filter.setFilesNotUnderContentRoot(newParent);

    fireMoved(f, oldParent, newParent);

    assertFalse(vcs.hasEntry("myRoot/file"));
    assertFalse(vcs.hasEntry("anotherRoot/file"));
  }

  @Test
  public void testMovingFilteredFileToOutsideOfTheContentRoots() {
    vcs.createDirectory("myRoot");

    TestVirtualFile f = testFile("file", "content");
    TestVirtualFile oldParent = testDir("myRoot");
    TestVirtualFile newParent = testDir("anotherRoot");
    newParent.addChild(f);

    filter.setFilesNotUnderContentRoot(newParent);
    filter.setNotAllowedFiles(f);

    fireMoved(f, oldParent, newParent);
    assertFalse(vcs.hasEntry("myRoot/file"));
  }

  @Test
  public void testMovingAroundOutsideContentRoots() {
    TestVirtualFile f = testFile("file", "content");
    TestVirtualFile oldParent = testDir("root1");
    TestVirtualFile newParent = testDir("root2");
    newParent.addChild(f);

    filter.setFilesNotUnderContentRoot(oldParent, newParent);

    fireMoved(f, oldParent, newParent);

    assertFalse(vcs.hasEntry("root1/file"));
    assertFalse(vcs.hasEntry("root2/file"));
  }

  @Test
  public void testDeletionFromDirectory() {
    vcs.createDirectory("dir");
    long timestamp = -1;
    vcs.createFile("file", null, timestamp, false);

    TestVirtualFile dir = testDir("dir");
    TestVirtualFile f = testFile("file");
    dir.addChild(f);
    fireDeletion(f, dir);

    assertTrue(vcs.hasEntry("dir"));
    assertFalse(vcs.hasEntry("dir/file"));
  }

  @Test
  public void testDeletionWithoutParent() {
    long timestamp = -1;
    vcs.createFile("file", null, timestamp, false);

    fireDeletion(testFile("file"));

    assertFalse(vcs.hasEntry("file"));
  }

  @Test
  public void testDeletionOfFileThanIsNotUnderVcsDoesNotThrowException() {
    fireDeletion(testFile("non-existent")); // should'n throw
  }

  @Test
  public void testFilteringFiles() {
    vcs = createMock(LocalVcs.class);
    replay(vcs);

    setUp();

    VirtualFile f = testFile("file");
    filter.setFilesNotUnderContentRoot(f);

    fireCreated(f);
    fireContentChanged(f);
    fireMoved(f, f, f);

    verify(vcs);
  }
}
