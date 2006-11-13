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
    root.doCreateDirectory(99, p("dir"));
    Change c = new CreateFileChange(1, p("dir/name"), null);
    c.applyTo(root);

    assertElements(new IdPath[]{idp(99, 1)}, c.getAffectedEntryIdPaths());

    assertTrue(c.affects(root.getEntry(1)));
    assertTrue(c.affects(root.getEntry(99)));
  }

  //@Test
  //public void testCollectingDifferencesForCreateFileChange() {
  //  Change c = new CreateFileChange(1, p("file"), null);
  //  c.applyTo(root);
  //
  //  List<Difference> d = c.getDifferencesFor(root.getEntry(p("file")));
  //  assertEquals(1, d.size());
  //}
  //
  //@Test
  //public void testCollectingDifferencesForCreateFileChangeForUnaffectedFile() {
  //  Change c = new CreateFileChange(1, p("file"), null);
  //  c.applyTo(root);
  //
  //  root.doCreateFile(2, p("another file"), null);
  //
  //  List<Difference> d = c.getDifferencesFor(root.getEntry(p("another file")));
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
  //  List<Difference> d = c.getDifferencesFor(root.getEntry(p("dir")));
  //  assertEquals(1, d.size());
  //}

  @Test
  public void testAffectedEntryIdForCreateDirectoryChange() {
    Change c = new CreateDirectoryChange(2, p("name"));
    c.applyTo(root);

    assertElements(new IdPath[]{idp(2)}, c.getAffectedEntryIdPaths());
  }

  @Test
  public void testAffectedEntryIdForChangeFileContentChange() {
    root.doCreateFile(16, p("file"), "content");

    Change c = new ChangeFileContentChange(p("file"), "new content");
    c.applyTo(root);

    assertElements(new IdPath[]{idp(16)}, c.getAffectedEntryIdPaths());
  }

  @Test
  public void testAffectedEntryIdForRenameChange() {
    root.doCreateFile(42, p("name"), null);

    Change c = new RenameChange(p("name"), "new name");
    c.applyTo(root);

    assertElements(new IdPath[]{idp(42)}, c.getAffectedEntryIdPaths());
  }

  @Test
  public void testAffectedEntryIdForMoveChange() {
    root.doCreateDirectory(1, p("dir1"));
    root.doCreateDirectory(2, p("dir2"));
    root.doCreateFile(13, p("dir1/file"), null);

    Change c = new MoveChange(p("dir1/file"), p("dir2"));
    c.applyTo(root);

    assertElements(new IdPath[]{idp(1, 13), idp(2, 13)},
                   c.getAffectedEntryIdPaths());
  }

  @Test
  public void testAffectedEntryIdForDeleteChange() {
    root.doCreateFile(7, p("file"), null);

    Change c = new DeleteChange(p("file"));
    c.applyTo(root);

    assertElements(new IdPath[]{idp(7)}, c.getAffectedEntryIdPaths());
  }
}