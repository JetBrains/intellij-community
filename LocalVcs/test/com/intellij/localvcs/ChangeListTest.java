package com.intellij.localvcs;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class ChangeListTest extends TestCase {
  private RootEntry r;
  private ChangeList cl;

  @Before
  public void setUp() {
    r = new RootEntry();
    cl = new ChangeList();
  }

  @Test
  public void testReverting() {
    cl.applyChangeSetTo(r, cs(new CreateFileChange(1, "file1", null, null)));
    cl.labelLastChangeSet("1");

    cl.applyChangeSetTo(r, cs(new CreateFileChange(2, "file2", null, null)));
    cl.labelLastChangeSet("2");

    RootEntry copy = r.copy();
    cl._revertUpToChangeSetOn(copy, cl.getChangeSets().get(1));
    assertTrue(copy.hasEntry(1));
    assertTrue(copy.hasEntry(2));

    copy = r.copy();
    cl._revertUpToChangeSetOn(copy, cl.getChangeSets().get(0));
    assertTrue(copy.hasEntry(1));
    assertFalse(copy.hasEntry(2));
  }

  @Test
  public void tesChangesForFile() {
    cl.applyChangeSetTo(r, cs(new CreateFileChange(1, "file", null, null)));
    cl.labelLastChangeSet("1");

    cl.applyChangeSetTo(r, cs(new ChangeFileContentChange("file", null, null)));
    cl.labelLastChangeSet("2");

    List<ChangeSet> result = getChangeSetsFor("file");

    assertEquals(2, result.size());

    assertEquals("1", result.get(0).getLabel());
    assertEquals("2", result.get(1).getLabel());
  }

  @Test
  public void testSeveralChangesForSameFileInOneChangeSet() {
    cl.applyChangeSetTo(r, cs(new CreateFileChange(1, "file", null, null),
                              new ChangeFileContentChange("file", null, null)));

    assertEquals(1, getChangeSetsFor("file").size());
  }

  @Test
  public void testChangeSetsWithChangesForAnotherFile() {
    cl.applyChangeSetTo(r, cs(new CreateFileChange(1, "file1", null, null),
                              new CreateFileChange(2, "file2", null, null)));

    assertEquals(1, getChangeSetsFor("file1").size());
  }

  @Test
  public void testDoesNotIncludeNonrelativeChangeSet() {
    cl.applyChangeSetTo(r, cs(new CreateFileChange(1, "file1", null, null)));
    cl.labelLastChangeSet("1");

    cl.applyChangeSetTo(r, cs(new CreateFileChange(2, "file2", null, null)));
    cl.labelLastChangeSet("2");

    cl.applyChangeSetTo(r, cs(new ChangeFileContentChange("file1", null, null)));
    cl.labelLastChangeSet("3");

    List<ChangeSet> result = getChangeSetsFor("file1");
    assertEquals(2, result.size());

    assertEquals("1", result.get(0).getLabel());
    assertEquals("3", result.get(1).getLabel());
  }

  @Test
  public void testChangeSetsForDirectories() {
    cl.applyChangeSetTo(r, cs(new CreateDirectoryChange(1, "dir", null)));
    cl.applyChangeSetTo(r, cs(new CreateFileChange(2, "dir/file", null, null)));

    assertEquals(2, getChangeSetsFor("dir").size());
  }

  @Test
  public void testChangeSetsForDirectoriesWithFilesMovedAround() {
    cl.applyChangeSetTo(r, cs(new CreateDirectoryChange(1, "dir1", null),
                              new CreateDirectoryChange(2, "dir2", null)));
    cl.labelLastChangeSet("1");

    cl.applyChangeSetTo(r, cs(new CreateFileChange(3, "dir1/file", null, null)));
    cl.labelLastChangeSet("2");

    cl.applyChangeSetTo(r, cs(new MoveChange("dir1/file", "dir2", null)));
    cl.labelLastChangeSet("3");

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
    cl.applyChangeSetTo(r, cs(new CreateDirectoryChange(1, "dir1", null),
                              new CreateDirectoryChange(2, "dir2", null)));

    cl.applyChangeSetTo(r, cs(new CreateFileChange(3, "dir1/file", null, null)));
    cl.applyChangeSetTo(r, cs(new MoveChange("dir1/file", "dir2", null)));

    assertEquals(2, getChangeSetsFor("dir2/file").size());
  }

  @Test
  public void testChangingParentDoesNotChangesItsChildren() {
    cl.applyChangeSetTo(r, cs(new CreateDirectoryChange(1, "d1", null),
                              new CreateDirectoryChange(2, "d2", null),
                              new CreateFileChange(3, "d1/file", null, null)));

    assertEquals(1, getChangeSetsFor("d1/file").size());

    cl.applyChangeSetTo(r, cs(new MoveChange("d1", "d2", null)));

    assertEquals(1, getChangeSetsFor("d2/d1/file").size());
  }

  @Test
  public void testChangeSetsForComplexMovingCase() {
    cl.applyChangeSetTo(r, cs(new CreateDirectoryChange(1, "d1", null),
                              new CreateFileChange(2, "d1/file", null, null),
                              new CreateDirectoryChange(3, "d1/d11", null),
                              new CreateDirectoryChange(4, "d1/d12", null),
                              new CreateDirectoryChange(5, "d2", null)));

    cl.applyChangeSetTo(r, cs(new MoveChange("d1/file", "d1/d11", null)));
    cl.applyChangeSetTo(r, cs(new MoveChange("d1/d11/file", "d1/d12", null)));

    assertEquals(3, getChangeSetsFor("d1").size());
    assertEquals(3, getChangeSetsFor("d1/d12/file").size());
    assertEquals(3, getChangeSetsFor("d1/d11").size());
    assertEquals(2, getChangeSetsFor("d1/d12").size());
    assertEquals(1, getChangeSetsFor("d2").size());

    cl.applyChangeSetTo(r, cs(new MoveChange("d1/d12", "d2", null)));

    assertEquals(4, getChangeSetsFor("d1").size());
    assertEquals(3, getChangeSetsFor("d2/d12/file").size());
    assertEquals(2, getChangeSetsFor("d2").size());
    assertEquals(3, getChangeSetsFor("d2/d12").size());
  }

  private List<ChangeSet> getChangeSetsFor(String path) {
    return cl.getChangeListFor(r.getEntry(path)).getChangeSets();
  }
}
