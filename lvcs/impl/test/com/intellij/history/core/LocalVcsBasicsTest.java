package com.intellij.history.core;

import com.intellij.history.core.changes.CreateFileChange;
import com.intellij.history.core.changes.DeleteChange;
import com.intellij.history.core.changes.RenameChange;
import com.intellij.history.core.storage.UnavailableContent;
import com.intellij.history.core.tree.Entry;
import org.junit.Test;

public class LocalVcsBasicsTest extends LocalVcsTestCase {
  LocalVcs vcs = new InMemoryLocalVcs();

  @Test
  public void testIncrementingIdOnEntryCreation() {
    vcs.createDirectory("dir");
    long timestamp = -1;
    vcs.createFile("file", null, timestamp, false);

    int id1 = vcs.getEntry("dir").getId();
    int id2 = vcs.getEntry("file").getId();

    assertTrue(id2 > id1);
  }

  @Test
  public void testCreatingFile() {
    vcs.createFile("file", cf("content"), 123L, true);

    Entry e = vcs.findEntry("file");

    assertNotNull(e);
    assertEquals(c("content"), e.getContent());
    assertEquals(123L, e.getTimestamp());
    assertTrue(e.isReadOnly());
  }

  @Test
  public void testCreatingLongFiles() {
    vcs.createFile("file", bigContentFactory(), 777L, false);

    Entry e = vcs.findEntry("file");

    assertNotNull(e);
    assertEquals(UnavailableContent.class, e.getContent().getClass());
    assertEquals(777L, e.getTimestamp());
  }

  @Test
  public void testCreatingDirectory() {
    vcs.createDirectory("dir");

    Entry e = vcs.findEntry("dir");
    assertNotNull(e);
    assertTrue(e.isDirectory());
  }

  @Test
  public void testCreatingFileUnderDirectory() {
    vcs.createDirectory("dir");
    long timestamp = -1;
    vcs.createFile("dir/file", null, timestamp, false);

    assertTrue(vcs.hasEntry("dir/file"));
  }

  @Test
  public void testChangingFileContent() {
    long timestamp = -1;
    vcs.createFile("file", cf("content"), timestamp, false);
    assertEquals(c("content"), vcs.getEntry("file").getContent());

    vcs.changeFileContent("file", cf("new content"), -1);
    assertEquals(c("new content"), vcs.getEntry("file").getContent());
  }

  @Test
  public void testChangingFileContentToEqualOneDoesNothing() {
    long timestamp = -1;
    vcs.createFile("f", cf("content"), timestamp, false);
    assertEquals(1, vcs.getRevisionsFor("f").size());

    vcs.changeFileContent("f", cf("content"), -1);
    assertEquals(1, vcs.getRevisionsFor("f").size());
  }

  @Test
  public void testRenamingFile() {
    long timestamp = -1;
    vcs.createFile("file", null, timestamp, false);
    assertTrue(vcs.hasEntry("file"));

    vcs.rename("file", "new file");

    assertFalse(vcs.hasEntry("file"));
    assertTrue(vcs.hasEntry("new file"));
  }

  @Test
  public void testRenamingDirectoryWithContent() {
    vcs.createDirectory("dir1");
    vcs.createDirectory("dir1/dir2");
    long timestamp = -1;
    vcs.createFile("dir1/dir2/file", null, timestamp, false);

    vcs.rename("dir1/dir2", "new dir");

    assertTrue(vcs.hasEntry("dir1/new dir"));
    assertTrue(vcs.hasEntry("dir1/new dir/file"));

    assertFalse(vcs.hasEntry("dir1/dir2"));
  }
  
  @Test
  public void testChangingROStatus() {
    vcs.createFile("f", null, -1, false);

    vcs.changeROStatus("f", true);
    assertTrue(vcs.getEntry("f").isReadOnly());

    vcs.changeROStatus("f", false);
    assertFalse(vcs.getEntry("f").isReadOnly());
  }

  @Test
  public void testTreatingRenamedAndCreatedFilesWithSameNameDifferently() {
    long timestamp = -1;
    vcs.createFile("file1", null, timestamp, false);
    vcs.rename("file1", "file2");
    long timestamp1 = -1;
    vcs.createFile("file1", null, timestamp1, false);

    Entry one = vcs.getEntry("file1");
    Entry two = vcs.getEntry("file2");

    assertNotSame(one, two);
  }

  @Test
  public void testMovingFileFromOneDirectoryToAnother() {
    vcs.createDirectory("dir1");
    vcs.createDirectory("dir2");
    long timestamp = -1;
    vcs.createFile("dir1/file", null, timestamp, false);

    vcs.move("dir1/file", "dir2");

    assertTrue(vcs.hasEntry("dir2/file"));
    assertFalse(vcs.hasEntry("dir1/file"));
  }

  @Test
  public void testMovingDirectory() {
    vcs.createDirectory("root1");
    vcs.createDirectory("root2");
    vcs.createDirectory("root1/dir");
    long timestamp = -1;
    vcs.createFile("root1/dir/file", null, timestamp, false);

    vcs.move("root1/dir", "root2");

    assertTrue(vcs.hasEntry("root2/dir"));
    assertTrue(vcs.hasEntry("root2/dir/file"));
    assertFalse(vcs.hasEntry("root1/dir"));
  }

  @Test
  public void testDeletingFile() {
    long timestamp = -1;
    vcs.createFile("file", cf("content"), timestamp, false);
    assertTrue(vcs.hasEntry("file"));

    vcs.delete("file");
    assertFalse(vcs.hasEntry("file"));
  }

  @Test
  public void testDeletingDirectoryWithContent() {
    vcs.createDirectory("dir1");
    vcs.createDirectory("dir1/dir2");
    long timestamp1 = -1;
    vcs.createFile("dir1/file1", cf("content1"), timestamp1, false);
    long timestamp = -1;
    vcs.createFile("dir1/dir2/file2", cf("content2"), timestamp, false);

    vcs.delete("dir1");
    assertFalse(vcs.hasEntry("dir1"));
    assertFalse(vcs.hasEntry("dir1/dir2"));
    assertFalse(vcs.hasEntry("dir1/file1"));
    assertFalse(vcs.hasEntry("dir1/dir2/file2"));
  }

  public void testDeletingAndAddingSameFile() {
    long timestamp1 = -1;
    vcs.createFile("file", null, timestamp1, false);
    vcs.delete("file");
    long timestamp = -1;
    vcs.createFile("file", null, timestamp, false);

    assertTrue(vcs.hasEntry("file"));
  }

  @Test
  public void testTreatingDeletedAndCreatedFilesWithSameNameDifferently() {
    long timestamp1 = -1;
    vcs.createFile("file", null, timestamp1, false);

    Entry one = vcs.getEntry("file");

    vcs.delete("file");
    long timestamp = -1;
    vcs.createFile("file", null, timestamp, false);

    Entry two = vcs.getEntry("file");

    assertNotSame(one, two);
  }

  @Test
  public void testRestoringDeletedFile() {
    vcs.createDirectory("dir");
    long timestamp = -1;
    vcs.createFile("dir/f", null, timestamp, false);
    Entry e = vcs.getEntry("dir/f");

    vcs.delete("dir/f");
    vcs.restoreFile(e.getId(), "dir/f", cf("content"), 123, true);

    Entry restored = vcs.findEntry("dir/f");
    assertNotNull(restored);
    assertEquals(e.getId(), restored.getId());
    assertEquals(c("content"), restored.getContent());
    assertEquals(123, restored.getTimestamp());
    assertTrue(restored.isReadOnly());
  }

  @Test
  public void testRestoringDeletedDirectory() {
    vcs.createDirectory("dir");
    vcs.createDirectory("dir/subDir");
    Entry e = vcs.getEntry("dir/subDir");

    vcs.delete("dir/subDir");
    vcs.restoreDirectory(e.getId(), "dir/subDir");

    Entry restored = vcs.findEntry("dir/subDir");
    assertNotNull(restored);
    assertTrue(restored.isDirectory());
    assertEquals(e.getId(), restored.getId());
  }

  @Test
  public void testCreatingRoots() {
    vcs.createDirectory("c:/dir/root");

    assertTrue(vcs.hasEntry("c:/dir/root"));
    assertFalse(vcs.hasEntry("c:/dir"));

    assertEquals("c:/dir/root", vcs.getRoots().get(0).getName());
  }

  @Test
  public void testCreatingFilesUnderRoots() {
    vcs.createDirectory("c:/dir/root");
    long timestamp = -1;
    vcs.createFile("c:/dir/root/file", null, timestamp, false);

    assertTrue(vcs.hasEntry("c:/dir/root/file"));
  }

  @Test
  public void testLastChange() {
    long timestamp = -1;
    vcs.createFile("f", null, timestamp, false);
    assertEquals(CreateFileChange.class, vcs.getLastChange().getClass());

    vcs.beginChangeSet();
    assertEquals(CreateFileChange.class, vcs.getLastChange().getClass());

    vcs.rename("f", "f2");
    assertEquals(RenameChange.class, vcs.getLastChange().getClass());

    vcs.endChangeSet(null);
    assertEquals(RenameChange.class, vcs.getLastChange().getClass());

    vcs.delete("f2");
    assertEquals(DeleteChange.class, vcs.getLastChange().getClass());
  }
}