package com.intellij.localvcs;

import org.junit.Before;
import org.junit.Test;

public class ChangesTest extends TestCase {
  private RootEntry root;

  @Before
  public void setUp() {
    root = new RootEntry();
  }

  @Test
  public void testAffectedEntryIdForCreateFileChange() {
    root.createDirectory(99, "dir", null);
    Change c = new CreateFileChange(1, "dir/name", null, null);
    c.applyTo(root);

    assertElements(new IdPath[]{idp(99, 1)}, c.getAffectedEntryIdPaths());

    assertTrue(c.affects(root.getEntry(1)));
    assertTrue(c.affects(root.getEntry(99)));
  }

  @Test
  public void testAffectedEntryForCreateDirectoryChange() {
    Change c = new CreateDirectoryChange(2, "name", null);
    c.applyTo(root);

    assertElements(new IdPath[]{idp(2)}, c.getAffectedEntryIdPaths());
  }

  @Test
  public void testAffectedEntryForChangeFileContentChange() {
    root.createFile(16, "file", "content", null);

    Change c = new ChangeFileContentChange("file", "new content", null);
    c.applyTo(root);

    assertElements(new IdPath[]{idp(16)}, c.getAffectedEntryIdPaths());
  }

  @Test
  public void testAffectedEntryForRenameChange() {
    root.createFile(42, "name", null, null);

    Change c = new RenameChange("name", "new name", null);
    c.applyTo(root);

    assertElements(new IdPath[]{idp(42)}, c.getAffectedEntryIdPaths());
  }

  @Test
  public void testAffectedEntryForMoveChange() {
    root.createDirectory(1, "dir1", null);
    root.createDirectory(2, "dir2", null);
    root.createFile(13, "dir1/file", null, null);

    Change c = new MoveChange("dir1/file", "dir2");
    c.applyTo(root);

    assertElements(new IdPath[]{idp(1, 13), idp(2, 13)},
                   c.getAffectedEntryIdPaths());
  }

  @Test
  public void testAffectedEntryForDeleteChange() {
    root.createDirectory(99, "dir", null);
    root.createFile(7, "dir/file", null, null);

    Change c = new DeleteChange("dir/file");
    c.applyTo(root);

    assertElements(new IdPath[]{idp(99, 7)}, c.getAffectedEntryIdPaths());
  }

  //@Test
  //public void testCollectingDifferencesForCreateFileChange() {
  //  Change c = new CreateFileChange(1, p("file"), "content");
  //  c.applyTo(root);
  //
  //  root.doRename(1, "new file");
  //  root.doChangeFileContent(1, "new content");
  //
  //  List<Difference> d = c.getDifferences(root.getEntry(p("new file")));
  //
  //  assertEquals(1, d.size());
  //  assertTrue(d.get(0).isCreated());
  //}
  //
  //@Test
  //public void testCollectingDifferencesForCreateFileChangeForUnaffectedFile() {
  //  Change c = new CreateFileChange(1, p("file"), null);
  //  c.applyTo(root);
  //  root.doCreateFile(2, p("another file"), null);
  //
  //  List<Difference> d = c.getDifferences(root.getEntry(p("another file")));
  //  assertTrue(d.isEmpty());
  //}
  //
  //@Test
  //public void testCollectingDifferencesForCreateFileChangeInDirectory() {
  //  root.doCreateDirectory(1, p("dir"));
  //
  //  Change c = new CreateFileChange(2, p("dir/file"), null);
  //  c.applyTo(root);
  //
  //  List<Difference> d = c.getDifferences(root.getEntry(p("dir")));
  //
  //  assertEquals(1, d.size());
  //  assertTrue(d.get(0).isCreated());
  //}
  //
  //@Test
  //public void testCollectingDifferencesForDirectoryFileChange() {
  //  root.doCreateDirectory(1, p("dir1"));
  //  root.doCreateDirectory(2, p("dir2"));
  //
  //  Change c = new CreateDirectoryChange(3, p("dir2/dir3"));
  //  c.applyTo(root);
  //
  //  List<Difference> d1 = c.getDifferences(root.getEntry(p("dir1")));
  //  assertTrue(d1.isEmpty());
  //
  //  List<Difference> d2 = c.getDifferences(root.getEntry(p("dir2")));
  //  assertEquals(1, d2.size());
  //  assertTrue(d2.get(0).isCreated());
  //
  //  List<Difference> d3 = c.getDifferences(root.getEntry(p("dir2/dir3")));
  //  assertEquals(1, d3.size());
  //  assertTrue(d3.get(0).isCreated());
  //}
  //
  //@Test
  //public void testCollectingDifferencesForFileContentChange() {
  //  root.doCreateDirectory(1, p("dir"));
  //  root.doCreateFile(2, p("dir/file"), "a");
  //
  //  Change c = new ChangeFileContentChange(p("dir/file"), "b");
  //  c.applyTo(root);
  //
  //  root.doRename(2, "new file");
  //  root.doChangeFileContent(2, "c");
  //
  //  List<Difference> d1 = c.getDifferences(root.getEntry(p("dir/new file")));
  //  assertEquals(1, d1.size());
  //  assertTrue(d1.get(0).isModified());
  //
  //  List<Difference> d2 = c.getDifferences(root.getEntry(p("dir")));
  //  assertEquals(1, d2.size());
  //  assertTrue(d2.get(0).isModified());
  //}
  //
  //@Test
  //public void testCollectingDifferencesForRenameChange() {
  //  root.doCreateDirectory(1, p("dir"));
  //  root.doCreateFile(2, p("dir/file"), null);
  //
  //  Change c = new RenameChange(p("dir/file"), "new name");
  //  c.applyTo(root);
  //
  //  List<Difference> d1 = c.getDifferences(root.getEntry(p("dir/new name")));
  //  assertEquals(1, d1.size());
  //  assertTrue(d1.get(0).isModified());
  //
  //  List<Difference> d2 = c.getDifferences(root.getEntry(p("dir")));
  //  assertEquals(1, d2.size());
  //  assertTrue(d2.get(0).isModified());
  //}
  //
  //@Test
  //public void testCollectingDifferencesForMoveChange() {
  //  root.doCreateDirectory(1, p("dir1"));
  //  root.doCreateDirectory(2, p("dir2"));
  //  root.doCreateFile(3, p("dir1/file"), "content");
  //
  //  Change c = new MoveChange(p("dir1/file"), p("dir2"));
  //  c.applyTo(root);
  //
  //  root.doRename(3, "new file");
  //  root.doChangeFileContent(3, "new content");
  //
  //  List<Difference> d1 = c.getDifferences(root.getEntry(p("dir1")));
  //  assertEquals(1, d1.size());
  //  assertTrue(d1.get(0).isDeleted());
  //
  //  List<Difference> d2 = c.getDifferences(root.getEntry(p("dir2")));
  //  assertEquals(1, d2.size());
  //  assertTrue(d2.get(0).isCreated());
  //
  //  List<Difference> d3 = c.getDifferences(root.getEntry(p("dir2/new file")));
  //  assertEquals(1, d3.size());
  //  assertTrue(d3.get(0).isModified());
  //}
  //
  //@Test
  //public void testCollectingDifferencesForMoveChangeFromRoot() {
  //  root.doCreateFile(3, p("file"), null);
  //  root.doCreateDirectory(2, p("dir"));
  //
  //  Change c = new MoveChange(p("file"), p("dir"));
  //  c.applyTo(root);
  //
  //  List<Difference> d = c.getDifferences(root.getEntry(p("dir/file")));
  //
  //  assertEquals(1, d.size());
  //  assertTrue(d.get(0).isModified());
  //}
  //
  //@Test
  //public void testCollectingDifferencesForMoveChangeForRootDir() {
  //  root.doCreateDirectory(1, p("root"));
  //  root.doCreateDirectory(2, p("root/dir1"));
  //  root.doCreateDirectory(3, p("root/dir2"));
  //  root.doCreateFile(4, p("root/dir1/file"), null);
  //
  //  Change c = new MoveChange(p("root/dir1/file"), p("root/dir2"));
  //  c.applyTo(root);
  //
  //  List<Difference> d = c.getDifferences(root.getEntry(p("root")));
  //
  //  assertEquals(2, d.size());
  //  assertTrue(d.get(0).isDeleted());
  //  assertTrue(d.get(1).isCreated());
  //}
  //
  //@Test
  //public void testCollectingDifferencesForDeleteChange() {
  //  root.doCreateDirectory(1, p("dir"));
  //  root.doCreateFile(2, p("dir/file"), null);
  //
  //  Change c = new DeleteChange(p("dir/file"));
  //  c.applyTo(root);
  //
  //  List<Difference> d = c.getDifferences(root.getEntry(p("dir")));
  //
  //  assertEquals(1, d.size());
  //  assertTrue(d.get(0).isDeleted());
  //}
}