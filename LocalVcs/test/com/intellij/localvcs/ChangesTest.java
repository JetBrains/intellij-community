package com.intellij.localvcs;

import org.junit.Before;
import org.junit.Test;

public class ChangesTest extends TestCase {
  private RootEntry root;

  @Before
  public void setUp() {
    root = new RootEntry();
  }

  @Test
  public void testAffectedEntryIdForCreateFileChange() {
    Change c = new CreateFileChange(p("name"), null, 1);
    c.applyTo(root);

    assertEquals(1, c.getAffectedEntryId());
  }

  @Test
  public void testAffectedEntryIdForCreateDirectoryChange() {
    Change c = new CreateDirectoryChange(p("name"), 2);
    c.applyTo(root);

    assertEquals(2, c.getAffectedEntryId());
  }

  @Test
  public void testAffectedEntryIdForChangeFileContentChange() {
    root.doCreateFile(p("file"), "content", 16);

    Change c = new ChangeFileContentChange(p("file"), "new content");
    c.applyTo(root);

    assertEquals(16, c.getAffectedEntryId());
  }

  @Test
  public void testAffectedEntryIdForRenameChange() {
    root.doCreateFile(p("name"), null, 42);

    Change c = new RenameChange(p("name"), "new name");
    c.applyTo(root);

    assertEquals(42, c.getAffectedEntryId());
  }

  @Test
  public void testAffectedEntryIdForMoveChange() {
    root.doCreateFile(p("file"), null, 13);
    root.doCreateDirectory(p("dir"), null);

    Change c = new MoveChange(p("file"), p("dir"));
    c.applyTo(root);

    assertEquals(13, c.getAffectedEntryId());
  }

  @Test
  public void testAffectedEntryIdForDeleteChange() {
    root.doCreateFile(p("file"), null, 7);

    Change c = new DeleteChange(p("file"));
    c.applyTo(root);

    assertEquals(7, c.getAffectedEntryId());
  }
}