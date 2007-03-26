package com.intellij.localvcs;

import org.junit.Test;

import java.util.List;

public class ChangesPurgingTest extends LocalVcsTestCase {
  private RootEntry root = new RootEntry();

  @Test
  public void testChangeFileContentChange() {
    root.createFile(1, "f", c("old"), -1);

    Change c = new ChangeFileContentChange("f", c("new"), -1);
    c.applyTo(root);

    List<Content> cc = c.getContentsToPurge();

    assertEquals(1, cc.size());
    assertEquals(c("old"), cc.get(0));
  }

  @Test
  public void testDeleteFileChange() {
    root.createFile(1, "f", c("content"), -1);

    Change c = new DeleteChange("f");
    c.applyTo(root);

    List<Content> cc = c.getContentsToPurge();

    assertEquals(1, cc.size());
    assertEquals(c("content"), cc.get(0));
  }

  @Test
  public void testDeleteDirectoryWithFilesChange() {
    root.createDirectory(1, "dir");
    root.createDirectory(2, "dir/subDir");
    root.createFile(3, "dir/file", c("one"), -1);
    root.createFile(4, "dir/subDir/file1", c("two"), -1);
    root.createFile(5, "dir/subDir/file2", c("three"), -1);

    Change c = new DeleteChange("dir");
    c.applyTo(root);

    List<Content> cc = c.getContentsToPurge();

    assertEquals(3, cc.size());
    assertTrue(cc.contains(c("one")));
    assertTrue(cc.contains(c("two")));
    assertTrue(cc.contains(c("three")));
  }

  @Test
  public void testOtherChanges() {
    Change c1 = new CreateFileChange(1, "file", c("content"), -1);
    Change c2 = new CreateDirectoryChange(2, "dir");
    Change c3 = new MoveChange("file", "dir");
    Change c4 = new RenameChange("dir/file", "newFile");

    c1.applyTo(root);
    c2.applyTo(root);
    c3.applyTo(root);
    c4.applyTo(root);

    assertTrue(c1.getContentsToPurge().isEmpty());
    assertTrue(c2.getContentsToPurge().isEmpty());
    assertTrue(c3.getContentsToPurge().isEmpty());
    assertTrue(c4.getContentsToPurge().isEmpty());
  }
}