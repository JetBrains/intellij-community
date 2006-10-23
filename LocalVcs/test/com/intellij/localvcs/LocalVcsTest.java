package com.intellij.localvcs;

import java.io.File;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class LocalVcsTest extends TempDirTestCase {
  private File myFile;
  private LocalVcs myVcs;

  @Before
  public void setUp() {
    myFile = createFile("file");
    myVcs = new LocalVcs();
  }

  @Test
  public void testCommitting() {
    myVcs.addFile(myFile, "");
    assertFalse(myVcs.hasFile(myFile));

    myVcs.commit();
    assertTrue(myVcs.hasFile(myFile));
  }

  @Test
  public void testClearingUncommittedFiles() {
    assertTrue(myVcs.isClean());

    myVcs.addFile(myFile, "");
    assertFalse(myVcs.isClean());

    myVcs.commit();

    assertTrue(myVcs.isClean());
  }

  @Test
  public void testContent() {
    myVcs.addFile(myFile, "content");
    myVcs.commit();

    myVcs.addFile(myFile, "new content");

    assertEquals("content", myVcs.getFileContent(myFile));
  }

  @Test
  public void testContentOnUnknownFile() {
    assertNull(myVcs.getFileContent(myFile));
  }

  @Test
  public void testDelete() {
    myVcs.addFile(myFile, "content");
    myVcs.commit();

    myVcs.deleteFile(myFile);
    assertEquals("content", myVcs.getFileContent(myFile));

    myVcs.commit();
    assertNull(myVcs.getFileContent(myFile));
  }

  @Test
  public void testClearingDeletetFiles() {
    myVcs.addFile(myFile, "content");
    myVcs.commit();

    assertTrue(myVcs.isClean());

    myVcs.deleteFile(myFile);
    assertFalse(myVcs.isClean());

    myVcs.commit();
    assertTrue(myVcs.isClean());
  }
}
