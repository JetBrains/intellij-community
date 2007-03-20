package com.intellij.localvcs;

import org.junit.Test;

import java.util.List;

public class ChangeListTest extends LocalVcsTestCase {
  private RootEntry r = new RootEntry();
  private ChangeList cl = new ChangeList();

  @Test
  public void testRevertion() {
    applyAndAddChangeSet(cs("1", new CreateFileChange(1, "file1", null, null)));
    applyAndAddChangeSet(cs("2", new CreateFileChange(2, "file2", null, null)));

    RootEntry copy = r.copy();
    cl.revertUpToChangeSet(copy, cl.getChangeSets().get(1));
    assertTrue(copy.hasEntry("file1"));
    assertTrue(copy.hasEntry("file2"));

    copy = r.copy();
    cl.revertUpToChangeSet(copy, cl.getChangeSets().get(0));
    assertTrue(copy.hasEntry("file1"));
    assertFalse(copy.hasEntry("file2"));
  }

  @Test
  public void tesChangesForFile() {
    applyAndAddChangeSet(cs("1", new CreateFileChange(1, "file", null, null)));
    applyAndAddChangeSet(cs("2", new ChangeFileContentChange("file", null, null)));

    List<ChangeSet> result = getChangeSetsFor("file");

    assertEquals(2, result.size());

    assertEquals("1", result.get(0).getLabel());
    assertEquals("2", result.get(1).getLabel());
  }

  @Test
  public void testSeveralChangesForSameFileInOneChangeSet() {
    applyAndAddChangeSet(cs(new CreateFileChange(1, "file", null, null), new ChangeFileContentChange("file", null, null)));

    assertEquals(1, getChangeSetsFor("file").size());
  }

  @Test
  public void testChangeSetsWithChangesForAnotherFile() {
    applyAndAddChangeSet(cs(new CreateFileChange(1, "file1", null, null), new CreateFileChange(2, "file2", null, null)));

    assertEquals(1, getChangeSetsFor("file1").size());
  }

  @Test
  public void testDoesNotIncludeNonrelativeChangeSet() {
    applyAndAddChangeSet(cs("1", new CreateFileChange(1, "file1", null, null)));
    applyAndAddChangeSet(cs("2", new CreateFileChange(2, "file2", null, null)));
    applyAndAddChangeSet(cs("3", new ChangeFileContentChange("file1", null, null)));

    List<ChangeSet> result = getChangeSetsFor("file1");
    assertEquals(2, result.size());

    assertEquals("1", result.get(0).getLabel());
    assertEquals("3", result.get(1).getLabel());
  }

  @Test
  public void testChangeSetsForDirectories() {
    applyAndAddChangeSet(cs(new CreateDirectoryChange(1, "dir", null)));
    applyAndAddChangeSet(cs(new CreateFileChange(2, "dir/file", null, null)));

    assertEquals(2, getChangeSetsFor("dir").size());
  }

  @Test
  public void testChangeSetsForDirectoriesWithFilesMovedAround() {
    applyAndAddChangeSet(cs("1", new CreateDirectoryChange(1, "dir1", null), new CreateDirectoryChange(2, "dir2", null)));
    applyAndAddChangeSet(cs("2", new CreateFileChange(3, "dir1/file", null, null)));
    applyAndAddChangeSet(cs("3", new MoveChange("dir1/file", "dir2")));

    List<ChangeSet> cs1 = getChangeSetsFor("dir1");
    List<ChangeSet> cs2 = getChangeSetsFor("dir2");

    assertEquals(3, cs1.size());
    assertEquals("1", cs1.get(0).getLabel());
    assertEquals("2", cs1.get(1).getLabel());
    assertEquals("3", cs1.get(2).getLabel());

    assertEquals(2, cs2.size());
    assertEquals("1", cs2.get(0).getLabel());
    assertEquals("3", cs2.get(1).getLabel());
  }

  @Test
  public void testChangeSetsForMovedFiles() {
    applyAndAddChangeSet(cs(new CreateDirectoryChange(1, "dir1", null), new CreateDirectoryChange(2, "dir2", null)));

    applyAndAddChangeSet(cs(new CreateFileChange(3, "dir1/file", null, null)));
    applyAndAddChangeSet(cs(new MoveChange("dir1/file", "dir2")));

    assertEquals(2, getChangeSetsFor("dir2/file").size());
  }

  @Test
  public void testChangingParentDoesNotChangesItsChildren() {
    applyAndAddChangeSet(cs(new CreateDirectoryChange(1, "d1", null), new CreateDirectoryChange(2, "d2", null),
                            new CreateFileChange(3, "d1/file", null, null)));

    assertEquals(1, getChangeSetsFor("d1/file").size());

    applyAndAddChangeSet(cs(new MoveChange("d1", "d2")));

    assertEquals(1, getChangeSetsFor("d2/d1/file").size());
  }

  @Test
  public void testChangeSetsForComplexMovingCase() {
    applyAndAddChangeSet(cs(new CreateDirectoryChange(1, "d1", null), new CreateFileChange(2, "d1/file", null, null),
                            new CreateDirectoryChange(3, "d1/d11", null), new CreateDirectoryChange(4, "d1/d12", null),
                            new CreateDirectoryChange(5, "d2", null)));

    applyAndAddChangeSet(cs(new MoveChange("d1/file", "d1/d11")));
    applyAndAddChangeSet(cs(new MoveChange("d1/d11/file", "d1/d12")));

    assertEquals(3, getChangeSetsFor("d1").size());
    assertEquals(3, getChangeSetsFor("d1/d12/file").size());
    assertEquals(3, getChangeSetsFor("d1/d11").size());
    assertEquals(2, getChangeSetsFor("d1/d12").size());
    assertEquals(1, getChangeSetsFor("d2").size());

    applyAndAddChangeSet(cs(new MoveChange("d1/d12", "d2")));

    assertEquals(4, getChangeSetsFor("d1").size());
    assertEquals(3, getChangeSetsFor("d2/d12/file").size());
    assertEquals(2, getChangeSetsFor("d2").size());
    assertEquals(3, getChangeSetsFor("d2/d12").size());
  }

  @Test
  public void testChangeSetForFileMovedIntoCreatedDir() {
    ChangeSet cs1 = cs(new CreateFileChange(1, "file", null, null));
    ChangeSet cs2 = cs(new CreateDirectoryChange(2, "dir", null));
    ChangeSet cs3 = cs(new MoveChange("file", "dir"));
    applyAndAddChangeSet(cs1);
    applyAndAddChangeSet(cs2);
    applyAndAddChangeSet(cs3);

    assertEquals(2, getChangeSetsFor("dir/file").size());
    assertEquals(cs1, getChangeSetsFor("dir/file").get(0));
    assertEquals(cs3, getChangeSetsFor("dir/file").get(1));

    assertEquals(2, getChangeSetsFor("dir").size());
    assertEquals(cs2, getChangeSetsFor("dir").get(0));
    assertEquals(cs3, getChangeSetsFor("dir").get(1));
  }

  private void applyAndAddChangeSet(ChangeSet cs) {
    cs.applyTo(r);
    cl.addChangeSet(cs);
  }

  private List<ChangeSet> getChangeSetsFor(String path) {
    return cl.getChangeSetsFor(r.getEntry(path));
  }
}
