package com.intellij.localvcs;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class LocalVcsTest extends TempDirTestCase {
  private LocalVcs myVcs;

  @Before
  public void setUp() {
    myVcs = new LocalVcs(getTempDir());
  }

  @Test
  public void testEmptyWorkingRevision() {
    assertEquals(0, myVcs.getWorkingRevision().size());
  }

  @Test
  public void testFiles() {
    createFile("file");

    DirectoryRevision rev = myVcs.getWorkingRevision();
    assertEquals(1, rev.size());

    Revision fileRev = rev.get(0);
    assertEquals(WorkingFileRevision.class, fileRev.getClass());
    assertEquals("file", fileRev.getName());
  }

  @Test
  public void testDirs() {
    createDir("dir");
    createFile("dir/file");

    DirectoryRevision rev = myVcs.getWorkingRevision();
    assertEquals(1, rev.size());

    assertEquals(WorkingDirectoryRevision.class, rev.get(0).getClass());

    DirectoryRevision dirRev = ((DirectoryRevision)rev.get(0));
    assertEquals("dir", dirRev.getName());

    assertEquals(1, dirRev.size());

    assertEquals(WorkingFileRevision.class, dirRev.get(0).getClass());
    assertEquals("file", dirRev.get(0).getName());
  }

  @Test
  public void testCommitting() {
    assertTrue(myVcs.getRevisions().isEmpty());

    myVcs.commit();

    assertEquals(1, myVcs.getRevisions().size());
    assertEquals(TEMP_DIR_NAME,
                 myVcs.getRevisions().get(0).getName());
  }

  @Test
  public void testCommittingFiles() {
    createFile("file");

    myVcs.commit();
    deleteFile("file");

    StoredDirectoryRevision rev = myVcs.getRevisions().get(0);

    assertEquals(1, rev.size());
    assertEquals("file", rev.get(0).getName());
  }
}
