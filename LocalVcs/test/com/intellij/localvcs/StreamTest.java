package com.intellij.localvcs;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;

public class StreamTest extends TestCase {
  private Stream is;
  private Stream os;

  @Before
  public void setUpStreams() throws IOException {
    PipedOutputStream pos = new PipedOutputStream();
    PipedInputStream pis = new PipedInputStream(pos);

    os = new Stream(pos);
    is = new Stream(pis);
  }

  @Test
  public void testPath() throws IOException {
    Path p = new Path("dir/file");
    os.writePath(p);
    assertEquals(p, is.readPath());
  }

  @Test
  public void testIdPath() throws IOException {
    IdPath p = new IdPath(1, 2, 3);
    os.writeIdPath(p);
    assertEquals(p, is.readIdPath());
  }

  @Test
  public void testFileEntry() throws IOException {
    Entry e = new FileEntry(42, "file", "content", null);

    os.writeEntry(e);
    Entry result = is.readEntry();

    assertEquals(FileEntry.class, result.getClass());
    assertEquals(42, result.getId());
    assertEquals("file", result.getName());
    assertEquals("content", result.getContent());
  }

  @Test
  public void testDoesNotWriteEntryParent() throws IOException {
    Entry parent = new DirectoryEntry(null, null, null);
    Entry e = new FileEntry(42, "file", "content", null);

    parent.addChild(e);
    os.writeEntry(e);

    assertNull(is.readEntry().getParent());
  }

  @Test
  public void testEmptyDirectoryEntry() throws IOException {
    Entry e = new DirectoryEntry(13, "name", null);

    os.writeEntry(e);
    Entry result = is.readEntry();

    assertEquals(DirectoryEntry.class, result.getClass());
    assertEquals(13, result.getId());
    assertEquals("name", result.getName());
  }

  @Test
  public void testDirectoryEntryWithChildren() throws IOException {
    Entry dir = new DirectoryEntry(13, "dir", null);
    Entry subDir = new DirectoryEntry(66, "subDir", null);

    dir.addChild(new FileEntry(1, "subFile", "1", null));
    dir.addChild(subDir);
    subDir.addChild(new FileEntry(2, "subSubFile", "2", null));

    os.writeEntry(dir);
    Entry result = is.readEntry();

    List<Entry> children = result.getChildren();
    assertEquals(2, children.size());

    assertEquals(FileEntry.class, children.get(0).getClass());
    assertEquals("subFile", children.get(0).getName());
    assertEquals(p("dir/subFile"), children.get(0).getPath());

    assertEquals(DirectoryEntry.class, children.get(1).getClass());
    assertEquals("subDir", children.get(1).getName());
    assertEquals(p("dir/subDir"), children.get(1).getPath());

    assertEquals(1, children.get(1).getChildren().size());
    assertEquals("subSubFile", children.get(1).getChildren().get(0).getName());
  }

  @Test
  public void testRootEntryRootWithWithChildren() throws IOException {
    RootEntry e = new RootEntry("c:/root");

    e.addChild(new FileEntry(1, "file", "", null));
    e.addChild(new DirectoryEntry(2, "dir", null));

    os.writeEntry(e);
    Entry result = is.readEntry();

    assertEquals(RootEntry.class, result.getClass());
    assertNull(result.getId());
    assertEquals("c:/root", result.getName());

    List<Entry> children = result.getChildren();
    assertEquals(2, children.size());

    assertEquals(FileEntry.class, children.get(0).getClass());
    assertEquals("file", children.get(0).getName());
    assertEquals(p("c:/root/file"), children.get(0).getPath());

    assertEquals(DirectoryEntry.class, children.get(1).getClass());
    assertEquals("dir", children.get(1).getName());
    assertEquals(p("c:/root/dir"), children.get(1).getPath());
  }

  // todo test AffectedEntyPath saving for Changes 

  @Test
  public void testCreateFileChange() throws IOException {
    Change c = new CreateFileChange(null, "/file", "content", null);

    os.writeChange(c);
    Change result = is.readChange();

    assertEquals(CreateFileChange.class, result.getClass());

    assertEquals(p("/file"), ((CreateFileChange)result).getPath());
    assertEquals("content", ((CreateFileChange)result).getContent());
  }

  @Test
  public void testCreateDirectoryChange() throws IOException {
    Change c = new CreateDirectoryChange(null, "/dir", null);

    os.writeChange(c);
    Change result = is.readChange();

    assertEquals(CreateDirectoryChange.class, result.getClass());
    assertEquals(p("/dir"), ((CreateDirectoryChange)result).getPath());
  }

  @Test
  public void testAppliedDeleteChange() throws IOException {
    RootEntry root = new RootEntry("");

    root.createDirectory(1, "/entry", null);
    root.createFile(2, "/entry/file", "", null);
    root.createDirectory(3, "/entry/dir", null);

    Change c = new DeleteChange("/entry");
    c.applyTo(root);

    os.writeChange(c);
    Entry result = ((DeleteChange)is.readChange()).getAffectedEntry();

    assertEquals(DirectoryEntry.class, result.getClass());
    assertEquals("entry", result.getName());

    assertEquals(2, result.getChildren().size());
    assertEquals(p("entry/file"), result.getChildren().get(0).getPath());
    assertEquals(p("entry/dir"), result.getChildren().get(1).getPath());
  }

  @Test
  public void testAppliedChangeFileContentChange() throws IOException {
    RootEntry root = new RootEntry("");
    root.createFile(1, "/file", "content", null);

    Change c = new ChangeFileContentChange("/file", "new content", null);
    c.applyTo(root);

    os.writeChange(c);
    Change read = is.readChange();

    assertEquals(ChangeFileContentChange.class, read.getClass());

    ChangeFileContentChange result = ((ChangeFileContentChange)read);

    assertEquals(p("/file"), result.getPath());
    assertEquals("new content", result.getNewContent());

    assertEquals("content", result.getOldContent());
    assertElements(new IdPath[]{idp(1)}, result.getAffectedEntryIdPaths());
  }

  // todo test affected paths storing for all changes and other theirs props

  @Test
  public void testRenameChange() throws IOException {
    Change c = new RenameChange("/entry", "new name", null);

    os.writeChange(c);
    Change read = is.readChange();

    assertEquals(RenameChange.class, read.getClass());

    RenameChange result = ((RenameChange)read);

    assertEquals(p("/entry"), result.getPath());
    assertEquals("new name", result.getNewName());
  }

  @Test
  public void testMoveChange() throws IOException {
    Change c = new MoveChange("/entry", "/dir");

    os.writeChange(c);
    Change read = is.readChange();

    assertEquals(MoveChange.class, read.getClass());

    MoveChange result = ((MoveChange)read);

    assertEquals(p("/entry"), result.getPath());
    assertEquals(p("/dir"), result.getNewParentPath());
  }

  @Test
  public void testChangeSet() throws IOException {
    ChangeSet c = cs(new CreateFileChange(null, "/", "", null));
    c.setLabel("label");

    os.writeChangeSet(c);
    ChangeSet result = is.readChangeSet();

    assertEquals("label", result.getLabel());
    assertEquals(1, result.getChanges().size());
    assertEquals(CreateFileChange.class, result.getChanges().get(0).getClass());
  }

  @Test
  public void testEmptyChangeList() throws IOException {
    ChangeList c = new ChangeList();

    os.writeChangeList(c);
    ChangeList result = is.readChangeList();

    assertTrue(result.getChangeSets().isEmpty());
  }

  @Test
  public void testChangeList() throws IOException {
    ChangeList c = new ChangeList();
    c.applyChangeSetTo(new RootEntry(""), cs(new CreateFileChange(null, "/file", "content", null)));

    os.writeChangeList(c);
    ChangeList result = is.readChangeList();

    assertEquals(1, result.getChangeSets().size());
    assertEquals(1, result.getChangeSets().get(0).getChanges().size());
  }
}
