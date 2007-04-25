package com.intellij.localvcs.core;

import com.intellij.localvcs.core.storage.IContentStorage;
import org.junit.Test;

public class LocalVcsDatedAndMarkedContentTest extends LocalVcsTestCase {
  LocalVcs vcs = new TestLocalVcs();

  @Test
  public void testContentAtDate() {
    setCurrentTimestamp(10);
    vcs.createFile("f", b("one"), -1);
    setCurrentTimestamp(20);
    vcs.changeFileContent("f", b("two"), -1);

    assertEquals("one", new String(vcs.getByteContentAt("f", 10)));
    assertEquals("one", new String(vcs.getByteContentAt("f", 15)));
    assertEquals("one", new String(vcs.getByteContentAt("f", 19)));

    assertEquals("two", new String(vcs.getByteContentAt("f", 20)));
    assertEquals("two", new String(vcs.getByteContentAt("f", 100)));
  }

  @Test
  public void testContentAtDateForUnavailableContentIsNull() {
    setCurrentTimestamp(10);
    vcs.createFile("f", new byte[IContentStorage.MAX_CONTENT_LENGTH + 1], -1);

    assertNull(vcs.getByteContentAt("f", 20));
  }

  @Test
  public void testContentAtDateIfPurgedIsNull() {
    setCurrentTimestamp(10);
    vcs.createFile("f", b("one"), -1);
    setCurrentTimestamp(20);
    vcs.changeFileContent("f", b("two"), -1);

    vcs.purgeUpTo(15);

    assertNull(vcs.getByteContentAt("f", 10));
    assertEquals("two", new String(vcs.getByteContentAt("f", 20)));
  }

  @Test
  public void testMarkingFiles() {
    vcs.createFile("f", b("one"), -1);
    vcs.mark("f");
    vcs.changeFileContent("f", b("two"), -1);

    assertEquals("one", new String(vcs.getLastMarkedByteContent("f")));
  }

  @Test
  public void testMarkedContentForUnmarkedFileIsNull() {
    vcs.createFile("f", b("one"), -1);
    vcs.changeFileContent("f", b("two"), -1);

    assertNull(vcs.getLastMarkedByteContent("f"));
  }

  @Test
  public void testMarkedContentBigFileIsNull() {
    vcs.createFile("f", new byte[IContentStorage.MAX_CONTENT_LENGTH + 1], -1);
    vcs.mark("f");
    vcs.changeFileContent("f", b("two"), -1);

    assertNull(vcs.getLastMarkedByteContent("f"));
  }

  @Test
  public void testTakingLastMarkedContent() {
    vcs.createFile("f", b("one"), -1);
    vcs.mark("f");
    vcs.changeFileContent("f", b("two"), -1);
    vcs.mark("f");
    vcs.changeFileContent("f", b("three"), -1);

    assertEquals("two", new String(vcs.getLastMarkedByteContent("f")));
  }
}