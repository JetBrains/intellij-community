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
  public void testDoesNotRegisterChangeSetOnApplyingError() {
    CreateFileChange badChange = new CreateFileChange(null, null, null) {
      @Override
      public void applyTo(RootEntry root) {
        throw new SomeLocalVcsException();
      }
    };

    try {
      cl.applyChangeSetOn(r, cs(badChange));
    } catch (SomeLocalVcsException e) {}

    assertTrue(cl.getChangeSets().isEmpty());
  }

  // todo all the tests are incomplete

  @Test
  public void tesChangesForFile() {
    cl.applyChangeSetOn(r, cs(new CreateFileChange(1, p("file"), null)));
    cl.labelLastChangeSet("1");

    cl.applyChangeSetOn(r, cs(new ChangeFileContentChange(p("file"), null)));
    cl.labelLastChangeSet("2");

    List<ChangeSet> result = getChangeSetsFor("file");

    assertEquals(2, result.size());

    assertEquals("1", result.get(0).getLabel());
    assertEquals("2", result.get(1).getLabel());
  }

  @Test
  public void testSeveralChangesForSameFileInOneChangeSet() {
    cl.applyChangeSetOn(r, cs(new CreateFileChange(1, p("file"), null),
                              new ChangeFileContentChange(p("file"), null)));

    assertEquals(1, getChangeSetsFor("file").size());
  }

  @Test
  public void testChangeSetsWithChangesForAnotherFile() {
    cl.applyChangeSetOn(r, cs(new CreateFileChange(1, p("file1"), null),
                              new CreateFileChange(2, p("file2"), null)));

    assertEquals(1, getChangeSetsFor("file1").size());
  }

  @Test
  public void testDoesNotIncludeNonrelativeChangeSet() {
    cl.applyChangeSetOn(r, cs(new CreateFileChange(1, p("file1"), null)));
    cl.labelLastChangeSet("1");

    cl.applyChangeSetOn(r, cs(new CreateFileChange(2, p("file2"), null)));
    cl.labelLastChangeSet("2");

    cl.applyChangeSetOn(r, cs(new ChangeFileContentChange(p("file1"), null)));
    cl.labelLastChangeSet("3");

    List<ChangeSet> result = getChangeSetsFor("file1");
    assertEquals(2, result.size());

    assertEquals("1", result.get(0).getLabel());
    assertEquals("3", result.get(1).getLabel());
  }

  @Test
  public void testChangeSetsForDirectories() {
    cl.applyChangeSetOn(r, cs(new CreateDirectoryChange(1, p("dir"))));
    cl.applyChangeSetOn(r, cs(new CreateFileChange(2, p("dir/file"), null)));

    assertEquals(2, getChangeSetsFor("dir").size());
  }

  @Test
  public void testChangeSetsForDirectoriesWithFilesMovedAround() {
    cl.applyChangeSetOn(r, cs(new CreateDirectoryChange(1, p("dir1")),
                              new CreateDirectoryChange(2, p("dir2"))));
    cl.labelLastChangeSet("1");

    cl.applyChangeSetOn(r, cs(new CreateFileChange(3, p("dir1/file"), null)));
    cl.labelLastChangeSet("2");

    cl.applyChangeSetOn(r, cs(new MoveChange(p("dir1/file"), p("dir2"))));
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
    cl.applyChangeSetOn(r, cs(new CreateDirectoryChange(1, p("dir1")),
                              new CreateDirectoryChange(2, p("dir2"))));

    cl.applyChangeSetOn(r, cs(new CreateFileChange(3, p("dir1/file"), null)));
    cl.applyChangeSetOn(r, cs(new MoveChange(p("dir1/file"), p("dir2"))));

    assertEquals(2, getChangeSetsFor("dir2/file").size());
  }

  @Test
  public void testChangingParentDoesNotChangesItsChildren() {
    cl.applyChangeSetOn(r, cs(new CreateDirectoryChange(1, p("d1")),
                              new CreateDirectoryChange(2, p("d2")),
                              new CreateFileChange(3, p("d1/file"), null)));

    assertEquals(1, getChangeSetsFor("d1/file").size());

    cl.applyChangeSetOn(r, cs(new MoveChange(p("d1"), p("d2"))));

    assertEquals(1, getChangeSetsFor("d2/d1/file").size());
  }

  @Test
  public void testChangeSetsForComplexMovingCase() {
    cl.applyChangeSetOn(r, cs(new CreateDirectoryChange(1, p("d1")),
                              new CreateFileChange(2, p("d1/file"), null),
                              new CreateDirectoryChange(3, p("d1/d11")),
                              new CreateDirectoryChange(4, p("d1/d12")),
                              new CreateDirectoryChange(5, p("d2"))));

    cl.applyChangeSetOn(r, cs(new MoveChange(p("d1/file"), p("d1/d11"))));
    cl.applyChangeSetOn(r, cs(new MoveChange(p("d1/d11/file"), p("d1/d12"))));

    assertEquals(3, getChangeSetsFor("d1").size());
    assertEquals(3, getChangeSetsFor("d1/d12/file").size());
    assertEquals(3, getChangeSetsFor("d1/d11").size());
    assertEquals(2, getChangeSetsFor("d1/d12").size());
    assertEquals(1, getChangeSetsFor("d2").size());

    cl.applyChangeSetOn(r, cs(new MoveChange(p("d1/d12"), p("d2"))));

    assertEquals(4, getChangeSetsFor("d1").size());
    assertEquals(3, getChangeSetsFor("d2/d12/file").size());
    assertEquals(2, getChangeSetsFor("d2").size());
    assertEquals(3, getChangeSetsFor("d2/d12").size());
  }

  private List<ChangeSet> getChangeSetsFor(String path) {
    return cl.getChangeSetsFor(r.getEntry(p(path)));
  }

  private static class SomeLocalVcsException extends LocalVcsException {
  }
}
