package com.intellij.localvcs;

import java.io.IOException;

import org.junit.Test;

public class LocalVcsStorageTest extends TempDirTestCase {
  @Test
  public void testStoringEntries() throws IOException {
    vcs.createFile(p("file"), "content");
    vcs.commit();

    vcs.store(myTempDir);
    LocalVcs result = new LocalVcs(myTempDir);

    assertTrue(result.hasEntry(p("file")));
  }

  // TODO UNCOMMENT IT!!!
  //@Test
  public void testStoringChangeList() throws IOException {
    vcs.createFile(p("file"), "content");
    vcs.commit();
    vcs.changeFileContent(p("file"), "new content");
    vcs.commit();

    vcs.store(myTempDir);
    LocalVcs result = new LocalVcs(myTempDir);

    assertEquals("new content", result.getEntry(p("file")).getContent());

    result.revert();
    assertEquals("content", result.getEntry(p("file")).getContent());

    result.revert();
    assertFalse(result.hasEntry(p("file")));
  }

  @Test
  public void testStoringObjectsCounter() throws IOException {
    vcs.createFile(p("file1"), "content1");
    vcs.createFile(p("file2"), "content2");
    vcs.commit();

    vcs.store(myTempDir);
    LocalVcs result = new LocalVcs(myTempDir);

    result.createFile(p("file3"), "content3");
    result.commit();

    Integer id2 = result.getEntry(p("file2")).getObjectId();
    Integer id3 = result.getEntry(p("file3")).getObjectId();

    assertTrue(id2 < id3);
  }

  @Test
  public void testDoesNotStoreUncommittedChanges() throws IOException {
    vcs.createFile(p("file"), "content");

    vcs.store(myTempDir);
    LocalVcs result = new LocalVcs(myTempDir);

    assertTrue(result.isClean());
  }
}
