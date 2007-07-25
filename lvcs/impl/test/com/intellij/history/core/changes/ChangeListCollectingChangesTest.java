package com.intellij.history.core.changes;

import org.junit.Test;

import java.util.List;

public class ChangeListCollectingChangesTest extends ChangeListTestCase {
  @Test
  public void tesChangesForFile() {
    applyAndAdd(cs("1", new CreateFileChange(1, "file", null, -1)));
    applyAndAdd(cs("2", new ChangeFileContentChange("file", null, -1)));

    List<Change> result = getChangesFor("file");

    assertEquals(2, result.size());

    assertEquals("2", result.get(0).getName());
    assertEquals("1", result.get(1).getName());
  }

  @Test
  public void testSeveralChangesForSameFileInOneChangeSet() {
    applyAndAdd(cs(new CreateFileChange(1, "file", null, -1), new ChangeFileContentChange("file", null, -1)));

    assertEquals(1, getChangesFor("file").size());
  }

  @Test
  public void testChangeSetsWithChangesForAnotherFile() {
    applyAndAdd(cs(new CreateFileChange(1, "file1", null, -1), new CreateFileChange(2, "file2", null, -1)));

    assertEquals(1, getChangesFor("file1").size());
  }

  @Test
  public void testDoesNotIncludeNonrelativeChangeSet() {
    applyAndAdd(cs("1", new CreateFileChange(1, "file1", null, -1)));
    applyAndAdd(cs("2", new CreateFileChange(2, "file2", null, -1)));
    applyAndAdd(cs("3", new ChangeFileContentChange("file1", null, -1)));

    List<Change> result = getChangesFor("file1");
    assertEquals(2, result.size());

    assertEquals("3", result.get(0).getName());
    assertEquals("1", result.get(1).getName());
  }

  @Test
  public void testChangeSetsForDirectories() {
    applyAndAdd(cs(new CreateDirectoryChange(1, "dir")));
    applyAndAdd(cs(new CreateFileChange(2, "dir/file", null, -1)));

    assertEquals(2, getChangesFor("dir").size());
  }

  @Test
  public void testChangeSetsForDirectoriesWithFilesMovedAround() {
    applyAndAdd(cs("1", new CreateDirectoryChange(1, "dir1"), new CreateDirectoryChange(2, "dir2")));
    applyAndAdd(cs("2", new CreateFileChange(3, "dir1/file", null, -1)));
    applyAndAdd(cs("3", new MoveChange("dir1/file", "dir2")));

    List<Change> cc1 = getChangesFor("dir1");
    List<Change> cc2 = getChangesFor("dir2");

    assertEquals(3, cc1.size());
    assertEquals("3", cc1.get(0).getName());
    assertEquals("2", cc1.get(1).getName());
    assertEquals("1", cc1.get(2).getName());

    assertEquals(2, cc2.size());
    assertEquals("3", cc2.get(0).getName());
    assertEquals("1", cc2.get(1).getName());
  }

  @Test
  public void testChangeSetsForMovedFiles() {
    applyAndAdd(cs(new CreateDirectoryChange(1, "dir1"), new CreateDirectoryChange(2, "dir2")));

    applyAndAdd(cs(new CreateFileChange(3, "dir1/file", null, -1)));
    applyAndAdd(cs(new MoveChange("dir1/file", "dir2")));

    assertEquals(2, getChangesFor("dir2/file").size());
  }

  @Test
  public void testChangingParentChangesItsChildren() {
    applyAndAdd(cs(new CreateDirectoryChange(1, "d")));
    applyAndAdd(cs(new CreateFileChange(2, "d/file", null, -1)));

    assertEquals(1, getChangesFor("d/file").size());

    applyAndAdd(cs(new RenameChange("d", "dd")));

    assertEquals(2, getChangesFor("dd/file").size());
  }

  @Test
  public void testChangingPreviousParentDoesNotChangeItsChildren() {
    applyAndAdd(cs(new CreateDirectoryChange(1, "d1")));
    applyAndAdd(cs(new CreateDirectoryChange(2, "d2")));
    applyAndAdd(cs(new CreateFileChange(3, "d1/file", null, -1)));

    applyAndAdd(cs(new MoveChange("d1/file", "d2")));
    assertEquals(2, getChangesFor("d2/file").size());

    applyAndAdd(cs(new RenameChange("d1", "d11")));
    assertEquals(2, getChangesFor("d2/file").size());
  }

  @Test
  public void testDoesNotIncludePreviousParentChanges() {
    applyAndAdd(cs(new CreateDirectoryChange(1, "d")));
    applyAndAdd(cs(new RenameChange("d", "dd")));
    applyAndAdd(cs(new CreateFileChange(2, "dd/f", null, -1)));

    assertEquals(1, getChangesFor("dd/f").size());
  }

  @Test
  public void testDoesNotIncludePreviousChangesForNewParent() {
    applyAndAdd(cs(new CreateFileChange(1, "file", null, -1)));
    applyAndAdd(cs(new CreateDirectoryChange(2, "dir")));
    applyAndAdd(cs(new MoveChange("file", "dir")));

    assertEquals(2, getChangesFor("dir/file").size());
  }

  @Test
  public void testDoesNotIncludePreviousLabels() {
    applyAndAdd(cs(new PutLabelChange(null, -1)));
    applyAndAdd(cs(new CreateFileChange(1, "file", null, -1)));
    assertEquals(1, getChangesFor("file").size());
  }

  @Test
  public void testChangesForComplexMovingCase() {
    applyAndAdd(cs(new CreateDirectoryChange(1, "d1"), new CreateFileChange(2, "d1/file", null, -1), new CreateDirectoryChange(3, "d1/d11"),
                   new CreateDirectoryChange(4, "d1/d12"), new CreateDirectoryChange(5, "d2")));

    applyAndAdd(cs(new MoveChange("d1/file", "d1/d11")));
    applyAndAdd(cs(new MoveChange("d1/d11/file", "d1/d12")));

    assertEquals(3, getChangesFor("d1").size());
    assertEquals(3, getChangesFor("d1/d12/file").size());
    assertEquals(3, getChangesFor("d1/d11").size());
    assertEquals(2, getChangesFor("d1/d12").size());
    assertEquals(1, getChangesFor("d2").size());

    applyAndAdd(cs(new MoveChange("d1/d12", "d2")));

    assertEquals(4, getChangesFor("d1").size());
    assertEquals(4, getChangesFor("d2/d12/file").size());
    assertEquals(2, getChangesFor("d2").size());
    assertEquals(3, getChangesFor("d2/d12").size());
  }

  @Test
  public void testChangesForFileMovedIntoCreatedDir() {
    Change cs1 = cs(new CreateFileChange(1, "file", null, -1));
    Change cs2 = cs(new CreateDirectoryChange(2, "dir"));
    Change cs3 = cs(new MoveChange("file", "dir"));
    applyAndAdd(cs1, cs2, cs3);

    assertEquals(array(cs3, cs1), getChangesFor("dir/file"));
    assertEquals(array(cs3, cs2), getChangesFor("dir"));
  }

  @Test
  public void testChangesForRestoreFile() {
    Change cs1 = cs(new CreateFileChange(1, "file", null, -1));
    Change cs2 = cs(new ChangeFileContentChange("file", null, -1));
    Change cs3 = cs(new DeleteChange("file"));
    Change cs4 = cs(new CreateFileChange(1, "file", null, -1));
    Change cs5 = cs(new ChangeFileContentChange("file", null, -1));

    applyAndAdd(cs1, cs2, cs3, cs4, cs5);

    assertEquals(array(cs5, cs4, cs2, cs1), getChangesFor("file"));
  }

  @Test
  public void testChangesForFileRestoredSeveralTimes() {
    Change cs1 = cs(new CreateFileChange(1, "file", null, -1));
    Change cs2 = cs(new DeleteChange("file"));
    Change cs3 = cs(new CreateFileChange(1, "file", null, -1));
    Change cs4 = cs(new DeleteChange("file"));
    Change cs5 = cs(new CreateFileChange(1, "file", null, -1));

    applyAndAdd(cs1, cs2, cs3, cs4, cs5);

    assertEquals(array(cs5, cs3, cs1), getChangesFor("file"));
  }

  @Test
  public void testChangesForRestoredDirectory() {
    Change cs1 = cs(new CreateDirectoryChange(1, "dir"));
    Change cs2 = cs(new DeleteChange("dir"));
    Change cs3 = cs(new CreateDirectoryChange(1, "dir"));

    applyAndAdd(cs1, cs2, cs3);

    assertEquals(array(cs3, cs1), getChangesFor("dir"));
  }

  @Test
  public void testChangesForRestoredDirectoryWithRestoredChildren() {
    Change cs1 = cs(new CreateDirectoryChange(1, "dir"));
    Change cs2 = cs(new CreateFileChange(2, "dir/file", null, -1));
    Change cs3 = cs(new DeleteChange("dir"));
    Change cs4 = cs(new CreateDirectoryChange(1, "dir"));
    Change cs5 = cs(new CreateFileChange(2, "dir/file", null, -1));

    applyAndAdd(cs1, cs2, cs3, cs4, cs5);

    assertEquals(array(cs5, cs4, cs2, cs1), getChangesFor("dir"));
    assertEquals(array(cs5, cs2), getChangesFor("dir/file"));
  }

  @Test
  public void testChangesForFileIfThereWereSomeDeletedFilesBeforeItsCreation() {
    Change cs1 = cs(new CreateFileChange(1, "f1", null, -1));
    Change cs2 = cs(new DeleteChange("f1"));
    Change cs3 = cs(new CreateFileChange(2, "f2", null, -1));

    applyAndAdd(cs1, cs2, cs3);

    assertEquals(array(cs3), getChangesFor("f2"));
  }

  @Test
  public void testDoesNotIncludeChangeSetIfFileWasRestoredAndDeletedInOneChangeSet() {
    Change cs1 = cs(new CreateFileChange(1, "f", null, -1));
    Change cs2 = cs(new DeleteChange("f"));
    Change cs3 = cs(new CreateFileChange(1, "f", null, -1), new DeleteChange("f"));
    Change cs4 = cs(new CreateFileChange(1, "f", null, -1));

    applyAndAdd(cs1, cs2, cs3, cs4);

    assertEquals(array(cs4, cs1), getChangesFor("f"));
  }

  @Test
  public void testIncludingLabelsChanges() {
    Change cs1 = cs(new CreateFileChange(1, "f1", null, -1));
    Change cs2 = cs(new CreateFileChange(2, "f2", null, -1));
    Change cs3 = new PutEntryLabelChange("f1", "label", -1);
    Change cs4 = new PutLabelChange("label", -1);

    applyAndAdd(cs1, cs2, cs3, cs4);

    assertEquals(array(cs4, cs3, cs1), getChangesFor("f1"));
    assertEquals(array(cs4, cs2), getChangesFor("f2"));
  }

  @Test
  public void testIncludingChangeSetsWithLabelsInside() {
    Change cs1 = cs(new CreateFileChange(1, "f", null, -1));
    Change cs2 = cs(new PutLabelChange("label", -1));

    applyAndAdd(cs1, cs2);
    assertEquals(array(cs2, cs1), getChangesFor("f"));
  }

  @Test
  public void testDoesNotSplitChangeSetsWithLabelsInside() {
    Change cs1 = cs(new CreateFileChange(1, "f", null, -1));
    Change cs2 =
      cs(new ChangeFileContentChange("f", null, -1), new PutLabelChange("label", -1), new ChangeFileContentChange("f", null, -1));

    applyAndAdd(cs1, cs2);
    assertEquals(array(cs2, cs1), getChangesFor("f"));
  }

  @Test
  public void testDoesNotIncludeChangesMadeBetweenDeletionAndRestore() {
    Change cs1 = cs(new CreateFileChange(1, "file", null, -1));
    Change cs2 = cs(new DeleteChange("file"));
    Change cs3 = cs(new PutLabelChange(null, -1));
    Change cs4 = cs(new CreateFileChange(1, "file", null, -1));

    applyAndAdd(cs1, cs2, cs3, cs4);

    assertEquals(array(cs4, cs1), getChangesFor("file"));
  }

  @Test
  public void testDoesNotIgnoreDeletionOfChildren() {
    Change cs1 = cs(new CreateDirectoryChange(1, "dir"));
    Change cs2 = cs(new CreateFileChange(2, "dir/file", null, -1));
    Change cs3 = cs(new DeleteChange("dir/file"));

    applyAndAdd(cs1, cs2, cs3);

    assertEquals(array(cs3, cs2, cs1), getChangesFor("dir"));
  }

  private List<Change> getChangesFor(String path) {
    return cl.getChangesFor(r, path);
  }
}
