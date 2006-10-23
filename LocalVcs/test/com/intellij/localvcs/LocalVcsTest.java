package com.intellij.localvcs;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class LocalVcsTest {
  private String myFileName;
  private LocalVcs myVcs;

  @Before
  public void setUp() {
    myFileName = "file";
    myVcs = new LocalVcs();
  }

  @Test
  public void testCommitting() {
    myVcs.addFile(myFileName, "");
    assertFalse(myVcs.hasFile(myFileName));

    myVcs.commit();
    assertTrue(myVcs.hasFile(myFileName));
  }

  @Test
  public void testClearingAddedFiles() {
    assertTrue(myVcs.isClean());

    myVcs.addFile(myFileName, "");
    assertFalse(myVcs.isClean());

    myVcs.commit();

    assertTrue(myVcs.isClean());
  }

  @Test
  public void testContentBeforeCommit() {
    myVcs.addFile(myFileName, "content");
    myVcs.commit();

    myVcs.addFile(myFileName, "new content");

    assertEquals("content", myVcs.getFileContent(myFileName));
  }

  //@Test
  //public void testChangingContent() {
  //
  //}

  @Test
  public void testContentOnUnknownFile() {
    assertNull(myVcs.getFileContent(myFileName));
  }

  @Test
  public void testDelete() {
    myVcs.addFile(myFileName, "content");
    myVcs.commit();

    myVcs.deleteFile(myFileName);
    assertEquals("content", myVcs.getFileContent(myFileName));

    myVcs.commit();
    assertNull(myVcs.getFileContent(myFileName));
  }

  @Test
  public void testClearingDeletedFiles() {
    myVcs.addFile(myFileName, "content");
    myVcs.commit();

    assertTrue(myVcs.isClean());

    myVcs.deleteFile(myFileName);
    assertFalse(myVcs.isClean());

    myVcs.commit();
    assertTrue(myVcs.isClean());
  }
}
