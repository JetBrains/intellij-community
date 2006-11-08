package com.intellij.localvcs;

import org.junit.Before;
import org.junit.Test;

public class LocalVcsStoringTest extends TempDirTestCase {
  private LocalVcs vcs;

  @Before
  public void setUp() {
    vcs = createVcs();
  }

  private LocalVcs createVcs() {
    return new LocalVcs(new Storage(tempDir));
  }

  @Test
  public void testStoringOnApply() {
    final boolean[] isCalled = new boolean[]{false};

    LocalVcs vcs = new LocalVcs(new TestStorage()) {
      @Override
      protected void store() { isCalled[0] = true; }
    };

    vcs.apply();
    assertTrue(isCalled[0]);
  }

  @Test
  public void testStoringEntries() {
    vcs.createFile(p("file"), "content");
    vcs.apply();

    LocalVcs result = createVcs();

    assertTrue(result.hasEntry(p("file")));
  }

  @Test
  public void testStoringChangeList() {
    vcs.createFile(p("file"), "content");
    vcs.apply();
    vcs.changeFileContent(p("file"), "new content");
    vcs.apply();

    LocalVcs result = createVcs();

    assertEquals("new content", result.getEntry(p("file")).getContent());

    result.revert();
    assertEquals("content", result.getEntry(p("file")).getContent());

    result.revert();
    assertFalse(result.hasEntry(p("file")));
  }

  @Test
  public void testStoringObjectsCounter() {
    vcs.createFile(p("file1"), "content1");
    vcs.createFile(p("file2"), "content2");
    vcs.apply();

    LocalVcs result = createVcs();

    result.createFile(p("file3"), "content3");
    result.apply();

    Integer id2 = result.getEntry(p("file2")).getObjectId();
    Integer id3 = result.getEntry(p("file3")).getObjectId();

    assertTrue(id2 < id3);
  }

  @Test
  public void testDoesNotStoreUncommittedChanges() {
    vcs.createFile(p("file"), "content");
    vcs.store();

    LocalVcs result = createVcs();
    assertTrue(result.isClean());
  }
}
