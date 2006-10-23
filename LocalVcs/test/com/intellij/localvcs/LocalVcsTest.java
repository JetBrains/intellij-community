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
    assertFalse(myVcs.isDirty());

    myVcs.addFile(myFile, "");
    assertTrue(myVcs.isDirty());

    myVcs.commit();

    assertFalse(myVcs.isDirty());
  }

  @Test
  public void testContent() {
    myVcs.addFile(myFile, "content");
    myVcs.commit();

    myVcs.addFile(myFile, "new content");

    assertEquals("content", myVcs.getFileContent(myFile));
  }
}
