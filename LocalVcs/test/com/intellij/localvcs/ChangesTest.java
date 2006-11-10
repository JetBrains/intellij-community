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
    Change c = new CreateFileChange(1, p("name"), null);
    c.applyTo(root);

    assertEquals(idp(1), c.getAffectedEntryIdPath());
  }

  @Test
  public void testAffectedEntryIdForCreateDirectoryChange() {
    Change c = new CreateDirectoryChange(2, p("name"));
    c.applyTo(root);

    assertEquals(idp(2), c.getAffectedEntryIdPath());
  }

  @Test
  public void testAffectedEntryIdForChangeFileContentChange() {
    root.doCreateFile(16, p("file"), "content");

    Change c = new ChangeFileContentChange(p("file"), "new content");
    c.applyTo(root);

    assertEquals(idp(16), c.getAffectedEntryIdPath());
  }

  @Test
  public void testAffectedEntryIdForRenameChange() {
    root.doCreateFile(42, p("name"), null);

    Change c = new RenameChange(p("name"), "new name");
    c.applyTo(root);

    assertEquals(idp(42), c.getAffectedEntryIdPath());
  }

  @Test
  public void testAffectedEntryIdForMoveChange() {
    root.doCreateFile(13, p("file"), null);
    root.doCreateDirectory(null, p("dir"));

    Change c = new MoveChange(p("file"), p("dir"));
    c.applyTo(root);

    assertEquals(idp(13), c.getAffectedEntryIdPath());
  }

  @Test
  public void testAffectedEntryIdForDeleteChange() {
    root.doCreateFile(7, p("file"), null);

    Change c = new DeleteChange(p("file"));
    c.applyTo(root);

    assertEquals(idp(7), c.getAffectedEntryIdPath());
  }
}