package com.intellij.history.core.changes;

import com.intellij.history.core.LocalVcsTestCase;
import com.intellij.history.core.storage.Content;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.RootEntry;
import org.junit.Test;

import java.util.List;

public class StructuralChangesPurgingTest extends LocalVcsTestCase {
  private Entry root = new RootEntry();

  @Test
  public void testChangeFileContentChange() {
    createFile(root, 1, "f", c("old"), -1);

    Change c = new ContentChange("f", c("new"), -1);
    c.applyTo(root);

    List<Content> cc = c.getContentsToPurge();

    assertEquals(1, cc.size());
    assertEquals(c("old"), cc.get(0));
  }

  @Test
  public void testDeleteFileChange() {
    createFile(root, 1, "f", c("content"), -1);

    Change c = new DeleteChange("f");
    c.applyTo(root);

    List<Content> cc = c.getContentsToPurge();

    assertEquals(1, cc.size());
    assertEquals(c("content"), cc.get(0));
  }

  @Test
  public void testDeleteDirectoryWithFilesChange() {
    createDirectory(root, 1, "dir");
    createDirectory(root, 2, "dir/subDir");
    createFile(root, 3, "dir/file", c("one"), -1);
    createFile(root, 4, "dir/subDir/file1", c("two"), -1);
    createFile(root, 5, "dir/subDir/file2", c("three"), -1);

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
    Change c1 = new CreateFileChange(1, "file", c("content"), -1, false);
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
