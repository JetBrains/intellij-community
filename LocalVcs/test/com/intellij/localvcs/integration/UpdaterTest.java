package com.intellij.localvcs.integration;

import com.intellij.localvcs.*;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Test;

import java.util.List;

public class UpdaterTest extends LocalVcsTestCase {
  LocalVcs vcs = new TestLocalVcs();
  TestFileFilter filter = new TestFileFilter();

  @Test
  public void testAddingRoots() {
    updateWith(new TestVirtualFile("root", null));
    assertTrue(vcs.hasEntry("root"));
  }

  @Test
  public void testAddingSortedRoots() {
    TestVirtualFile root1 = new TestVirtualFile("root1", null);
    TestVirtualFile dir1 = new TestVirtualFile("dir1", null);
    TestVirtualFile root2 = new TestVirtualFile("root2", null);
    TestVirtualFile dir2 = new TestVirtualFile("dir2", null);
    TestVirtualFile root3 = new TestVirtualFile("root3", null);

    root1.addChild(dir1);
    dir1.addChild(root2);
    root2.addChild(dir2);
    dir2.addChild(root3);

    filter.setFilesNotUnderContentRoot(dir1, dir2);

    updateWith(root3, root2, root1);

    List<Entry> rr = vcs.getRoots();
    assertEquals(3, rr.size());
    assertEquals("root1", rr.get(0).getPath());
    assertEquals("root1/dir1/root2", rr.get(1).getPath());
    assertEquals("root1/dir1/root2/dir2/root3", rr.get(2).getPath());
  }

  @Test
  public void testDoesNotAddExistentRoots() {
    vcs.createDirectory("root", null);

    updateWith(new TestVirtualFile("root", null));
    assertEquals(1, vcs.getRoots().size());
  }

  @Test
  public void testDoesNotAddNestedRoots() {
    TestVirtualFile f1 = new TestVirtualFile("root", null);
    TestVirtualFile f2 = new TestVirtualFile("nested", null);
    f1.addChild(f2);
    updateWith(f1, f2);

    assertEquals(1, vcs.getRoots().size());
    assertEquals("root", vcs.getRoots().get(0).getPath());
    assertTrue(vcs.hasEntry("root/nested"));
  }

  @Test
  public void testAddingNestedRootsUnderExcludedDirectories() {
    TestVirtualFile root1 = new TestVirtualFile("root", null);
    TestVirtualFile dir = new TestVirtualFile("excluded", null);
    TestVirtualFile root2 = new TestVirtualFile("nested", null);
    root1.addChild(dir);
    dir.addChild(root2);

    filter.setFilesNotUnderContentRoot(dir);
    updateWith(root1, root2);

    assertEquals(2, vcs.getRoots().size());
    assertEquals("root", vcs.getRoots().get(0).getPath());
    assertEquals("root/excluded/nested", vcs.getRoots().get(1).getPath());

    assertFalse(vcs.hasEntry("root/excluded"));
  }

  @Test
  public void testExcludingInnerRootParentWhenRootsAreInReverseOrder() {
    vcs.createDirectory("outer", null);
    vcs.createDirectory("outer/dir", null);
    vcs.createDirectory("outer/dir/inner", null);

    TestVirtualFile outer = new TestVirtualFile("outer", null);
    TestVirtualFile dir = new TestVirtualFile("dir", null);
    TestVirtualFile inner = new TestVirtualFile("inner", null);

    outer.addChild(dir);
    dir.addChild(inner);

    filter.setFilesNotUnderContentRoot(dir);
    updateWith(inner, outer);

    assertEquals(2, vcs.getRoots().size());
    assertEquals("outer", vcs.getRoots().get(0).getPath());
    assertEquals("outer/dir/inner", vcs.getRoots().get(1).getPath());

    assertFalse(vcs.hasEntry("outer/dir"));
  }

  @Test
  public void testDeletingObsoleteRoots() {
    vcs.createDirectory("root", null);

    updateWith();
    assertFalse(vcs.hasEntry("root"));
  }

  @Test
  public void testDeletingOuterObsoleteRootsButLeavingInnerWithContent() {
    vcs.createDirectory("outer", null);
    vcs.createDirectory("outer/inner", null);

    TestVirtualFile inner = new TestVirtualFile("outer/inner", null);
    inner.addChild(new TestVirtualFile("file", null, null));
    updateWith(inner);

    assertFalse(vcs.hasEntry("outer"));
    assertTrue(vcs.hasEntry("outer/inner"));
    assertTrue(vcs.hasEntry("outer/inner/file"));
  }

  @Test
  public void testUpdatingRootsCaseSensitively() {
    vcs.createDirectory("root", null);

    Paths.setCaseSensitive(true);
    updateWith(new TestVirtualFile("ROOT", null));

    assertEquals(1, vcs.getRoots().size());
    assertEquals("ROOT", vcs.getRoots().get(0).getPath());
  }

  @Test
  public void testUpdatingRootsCaseInsensitively() {
    vcs.createDirectory("root", null);

    Paths.setCaseSensitive(false);
    updateWith(new TestVirtualFile("ROOT", null));

    assertEquals(1, vcs.getRoots().size());
    assertEquals("root", vcs.getRoots().get(0).getPath());
  }

  @Test
  public void testAddingNewFilesWithPhysicalContentAndTimestamp() {
    TestVirtualFile root = new TestVirtualFile("root", null);
    TestVirtualFile file = new TestVirtualFile("file", "virtual content", 123L);
    root.addChild(file);

    configureToReturnPhysicalContent("physical content");

    updateWith(root);

    Entry e = vcs.getEntry("root/file");
    assertEquals(c("physical content"), e.getContent());
    assertEquals(123L, e.getTimestamp());
  }

  @Test
  public void testAddingNewFilesRecursively() {
    TestVirtualFile root = new TestVirtualFile("root", null);
    TestVirtualFile dir = new TestVirtualFile("dir", null);
    TestVirtualFile file = new TestVirtualFile("file", null, null);

    root.addChild(dir);
    dir.addChild(file);

    updateWith(root);

    assertTrue(vcs.hasEntry("root"));
    assertTrue(vcs.hasEntry("root/dir"));
    assertTrue(vcs.hasEntry("root/dir/file"));
  }

  @Test
  public void testAddingNewFilesWhenSomeOfThemAlreadyExist() {
    TestVirtualFile root = new TestVirtualFile("root", null);
    TestVirtualFile dir = new TestVirtualFile("dir", null);
    TestVirtualFile file1 = new TestVirtualFile("file1", null, -1L);
    TestVirtualFile file2 = new TestVirtualFile("file2", null, -1L);

    root.addChild(dir);
    dir.addChild(file1);
    dir.addChild(file2);

    vcs.createDirectory("root", null);
    vcs.createDirectory("root/dir", null);
    vcs.createFile("root/dir/file1", null, -1L);

    updateWith(root);

    assertEquals(2, vcs.getEntry("root/dir").getChildren().size());

    assertTrue(vcs.hasEntry("root/dir/file1"));
    assertTrue(vcs.hasEntry("root/dir/file2"));
  }

  @Test
  public void testDoesNotAddFilteredFilesAndDirectories() {
    TestVirtualFile root = new TestVirtualFile("root", null);
    TestVirtualFile f1 = new TestVirtualFile("goodFile", null, null);
    TestVirtualFile f2 = new TestVirtualFile("badFile", null, null);
    TestVirtualFile f3 = new TestVirtualFile("badDir", null);
    root.addChild(f1);
    root.addChild(f2);
    root.addChild(f2);

    filter.setNotAllowedFiles(f2, f3);
    updateWith(root);

    assertTrue(vcs.hasEntry("root/goodFile"));
    assertFalse(vcs.hasEntry("root/badFile"));
    assertFalse(vcs.hasEntry("root/badDir"));
  }

  @Test
  public void testDoesNotAddFilesFromFilteredDirectories() {
    TestVirtualFile root = new TestVirtualFile("root", null);
    TestVirtualFile dir = new TestVirtualFile("dir", null);
    TestVirtualFile file = new TestVirtualFile("file", null, null);
    root.addChild(dir);
    dir.addChild(file);

    filter.setFilesNotUnderContentRoot(dir);
    updateWith(root);

    assertFalse(vcs.hasEntry("root/dir"));
    assertFalse(vcs.hasEntry("root/dir/file"));
  }

  @Test
  public void testDoesNotVisitFilteredDirectoriesOnAddition() {
    TestVirtualFile root = new TestVirtualFile("root", null);
    TestVirtualFile dir = new TestVirtualFile("dir", null) {
      @Override
      public VirtualFile[] getChildren() {
        fail();
        return null;
      }
    };
    root.addChild(dir);

    filter.setFilesNotUnderContentRoot(dir);

    // will throw an assertion if getChildren will be called
    updateWith(root);
  }

  @Test
  public void testDeletingObsoleteFilesRecursively() {
    TestVirtualFile root = new TestVirtualFile("root", null);
    TestVirtualFile dir = new TestVirtualFile("dir", null);
    TestVirtualFile file = new TestVirtualFile("fileToLeave", null, -1L);

    root.addChild(dir);
    dir.addChild(file);

    vcs.createDirectory("root", null);
    vcs.createDirectory("root/dir", null);
    vcs.createFile("root/dir/fileToLeave", null, -1L);
    vcs.createFile("root/dir/fileToDelete", null, -1L);
    vcs.createFile("root/dir/subdirToDelete", null, -1L);

    updateWith(root);

    assertTrue(vcs.hasEntry("root"));
    assertTrue(vcs.hasEntry("root/dir"));
    assertTrue(vcs.hasEntry("root/dir/fileToLeave"));
    assertFalse(vcs.hasEntry("root/dir/fileToDelete"));
    assertFalse(vcs.hasEntry("root/dir/subdirToDelete"));
  }

  @Test
  public void testDeletingFileAndCreatingDirectoryWithSameName() {
    TestVirtualFile root = new TestVirtualFile("root", null);
    root.addChild(new TestVirtualFile("name1", null));
    root.addChild(new TestVirtualFile("name2", null, null));

    vcs.createDirectory("root", null);
    vcs.createFile("root/name1", null, null);
    vcs.createDirectory("root/name2", null);

    updateWith(root);

    Entry e1 = vcs.findEntry("root/name1");
    Entry e2 = vcs.findEntry("root/name2");

    assertNotNull(e1);
    assertNotNull(e2);
    assertTrue(e1.isDirectory());
    assertFalse(e2.isDirectory());
  }

  @Test
  public void testDeletingFileAndCreatingDirectoryWithContentWithSameName() {
    TestVirtualFile root = new TestVirtualFile("root", null);
    TestVirtualFile dir = new TestVirtualFile("name", null);
    root.addChild(dir);
    dir.addChild(new TestVirtualFile("file", null, null));

    vcs.createDirectory("root", null);
    vcs.createDirectory("root/name", null);

    updateWith(root);

    assertTrue(vcs.hasEntry("root/name"));
    assertTrue(vcs.hasEntry("root/name/file"));
  }

  @Test
  public void testDeletingFilteredFilesAndDirectories() {
    TestVirtualFile root = new TestVirtualFile("root", null);
    TestVirtualFile file = new TestVirtualFile("file", null, null);
    TestVirtualFile dir = new TestVirtualFile("dir", null);
    TestVirtualFile dirFile = new TestVirtualFile("dirFile", null);
    root.addChild(dir);
    root.addChild(file);
    dir.addChild(dirFile);

    vcs.createDirectory("root", null);
    vcs.createFile("root/file", null, 111L);
    vcs.createDirectory("root/dir", null);
    vcs.createFile("root/dir/dirFile", null, 222L);

    filter.setFilesNotUnderContentRoot(file, dir);
    updateWith(root);

    assertFalse(vcs.hasEntry("root/file"));
    assertFalse(vcs.hasEntry("root/dir"));
    assertFalse(vcs.hasEntry("root/dir/dirFile"));
  }

  @Test
  public void testDoesNotVisitFilteredDirectoriesOnUpdate() {
    TestVirtualFile root = new TestVirtualFile("root", null);
    TestVirtualFile dir = new TestVirtualFile("dir", null) {
      @Override
      public VirtualFile[] getChildren() {
        fail();
        return null;
      }
    };
    root.addChild(dir);

    vcs.createDirectory("root", null);
    vcs.createDirectory("root/dir", null);

    filter.setFilesNotUnderContentRoot(dir);

    // will throw an assertion if getChildren will be called
    updateWith(root);
  }

  @Test
  public void testUpdatingOutdatedFilesWithPhysicalContent() {
    TestVirtualFile root = new TestVirtualFile("root", null);
    TestVirtualFile file = new TestVirtualFile("file", "virtual content", 666L);

    root.addChild(file);

    vcs.createDirectory("root", null);
    vcs.createFile("root/file", null, 111L);

    configureToReturnPhysicalContent("physical content");

    updateWith(root);

    Entry e = vcs.findEntry("root/file");
    assertEquals(c("physical content"), e.getContent());
    assertEquals(666L, e.getTimestamp());
  }

  @Test
  public void testUpdatingOutdatedFilesRecursively() {
    TestVirtualFile root = new TestVirtualFile("root", null);
    TestVirtualFile dir = new TestVirtualFile("dir", null);
    TestVirtualFile file = new TestVirtualFile("file", "new content", 333L);

    root.addChild(dir);
    dir.addChild(file);

    vcs.createDirectory("root", null);
    vcs.createDirectory("root/dir", null);
    vcs.createFile("root/dir/file", b("old content"), 111L);

    updateWith(root);

    Entry e = vcs.findEntry("root/dir/file");
    assertEquals(c("new content"), e.getContent());
    assertEquals(333L, e.getTimestamp());
  }

  @Test
  public void testDoesNotUpdateOutOfDateFiles() {
    TestVirtualFile root = new TestVirtualFile("root", null);
    TestVirtualFile file = new TestVirtualFile("file", "new content", 111L);

    root.addChild(file);

    vcs.createDirectory("root", null);
    vcs.createFile("root/file", b("old content"), 111L);

    updateWith(root);

    Entry e = vcs.findEntry("root/file");
    assertEquals(c("old content"), e.getContent());
    assertEquals(111L, e.getTimestamp());
  }

  @Test
  public void testUpdatingCaseInsensitive() {
    Paths.setCaseSensitive(false);

    TestVirtualFile root = new TestVirtualFile("root", null);
    root.addChild(new TestVirtualFile("FILE", null, 2L));

    vcs.createDirectory("root", null);
    vcs.createFile("root/file", null, 1L);

    updateWith(root);

    assertSame(vcs.getEntry("root/FILE"), vcs.getEntry("root/file"));
    assertEquals("FILE", vcs.getEntry("root/file").getName());
    assertEquals(2L, vcs.getEntry("root/file").getTimestamp());
  }

  @Test
  public void testUpdatingFileNameWhenCaseInsensitiveAndUpToDate() {
    Paths.setCaseSensitive(false);

    TestVirtualFile root = new TestVirtualFile("root", null);
    root.addChild(new TestVirtualFile("FILE", null, 1L));

    vcs.createDirectory("root", null);
    vcs.createFile("root/file", null, 1L);

    updateWith(root);

    assertEquals("FILE", vcs.getEntry("root/file").getName());
  }

  @Test
  public void testUpdatingCaseSensitive() {
    Paths.setCaseSensitive(true);

    TestVirtualFile root = new TestVirtualFile("root", null);
    root.addChild(new TestVirtualFile("FILE", null, 2L));

    vcs.createDirectory("root", null);
    vcs.createFile("root/file", null, 1L);

    updateWith(root);

    assertFalse(vcs.hasEntry("root/file"));

    assertTrue(vcs.hasEntry("root/FILE"));
    assertEquals(2L, vcs.getEntry("root/FILE").getTimestamp());
  }

  @Test
  public void testTreatingChangesDuringUpdateAsOne() {
    TestVirtualFile root = new TestVirtualFile("root", null);
    TestVirtualFile dir1 = new TestVirtualFile("dir1", null);
    TestVirtualFile dir2 = new TestVirtualFile("dir2", null);

    root.addChild(dir1);
    root.addChild(dir2);
    dir1.addChild(new TestVirtualFile("file1", null, null));
    dir2.addChild(new TestVirtualFile("file2", null, null));

    updateWith(root);
    assertEquals(1, vcs.getLabelsFor("root").size());
  }

  private String myPhysicalContent;

  private void updateWith(VirtualFile... roots) {
    Updater u = new Updater(vcs, filter, roots);
    CacheUpdaterHelper.performUpdate(u, myPhysicalContent);
  }

  private void configureToReturnPhysicalContent(String content) {
    myPhysicalContent = content;
  }
}
