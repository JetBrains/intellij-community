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
  public void testString() throws Exception {
    os.writeString("hello");
    os.writeString(null);

    assertEquals("hello", is.readString());
    assertNull(is.readString());
  }

  @Test
  public void testInteger() throws Exception {
    os.writeInteger(1);
    os.writeInteger(null);

    assertEquals(1, is.readInteger());
    assertNull(is.readInteger());
  }

  @Test
  public void testLong() throws Exception {
    os.writeLong(1L);
    os.writeLong(null);

    assertEquals(1L, is.readLong());
    assertNull(is.readLong());
  }

  @Test
  public void testIdPath() throws Exception {
    IdPath p = new IdPath(1, 2, 3);
    os.writeIdPath(p);
    assertEquals(p, is.readIdPath());
  }

  @Test
  public void testFileEntry() throws Exception {
    Entry e = new FileEntry(42, "file", "content", 123L);

    os.writeEntry(e);
    Entry result = is.readEntry();

    assertEquals(FileEntry.class, result.getClass());

    assertEquals(42, result.getId());
    assertEquals("file", result.getName());
    assertEquals("content", result.getContent());
    assertEquals(123L, result.getTimestamp());
  }

  @Test
  public void testDoesNotWriteEntryParent() throws IOException {
    Entry parent = new DirectoryEntry(null, null, null);
    Entry e = new FileEntry(42, null, null, null);

    parent.addChild(e);
    os.writeEntry(e);

    assertNull(is.readEntry().getParent());
  }

  @Test
  public void testEmptyDirectoryEntry() throws IOException {
    Entry e = new DirectoryEntry(13, "name", 666L);

    os.writeEntry(e);
    Entry result = is.readEntry();

    assertEquals(DirectoryEntry.class, result.getClass());

    assertEquals(13, result.getId());
    assertEquals("name", result.getName());
    assertEquals(666L, result.getTimestamp());
  }

  @Test
  public void testDirectoryEntryWithChildren() throws IOException {
    Entry dir = new DirectoryEntry(1, null, null);
    Entry subDir = new DirectoryEntry(2, null, null);
    dir.addChild(subDir);
    subDir.addChild(new FileEntry(3, "a", null, null));
    subDir.addChild(new FileEntry(4, "b", null, null));

    os.writeEntry(dir);
    Entry result = is.readEntry();

    List<Entry> children = result.getChildren();
    assertEquals(1, children.size());

    Entry e = children.get(0);
    assertEquals(DirectoryEntry.class, e.getClass());
    assertEquals(2, e.getId());
    assertSame(result, e.getParent());

    children = e.getChildren();
    assertEquals(2, children.size());

    assertEquals(FileEntry.class, children.get(0).getClass());
    assertEquals(3, children.get(0).getId());
    assertSame(e, children.get(0).getParent());

    assertEquals(FileEntry.class, children.get(1).getClass());
    assertEquals(4, children.get(1).getId());
    assertSame(e, children.get(1).getParent());
  }

  @Test
  public void testRootEntryWithRootWithChildren() throws IOException {
    RootEntry r = new RootEntry();

    r.createDirectory(1, "c:/root", null);
    r.createFile(2, "c:/root/file", null, null);

    os.writeEntry(r);
    Entry read = is.readEntry();

    assertEquals(RootEntry.class, read.getClass());

    RootEntry result = (RootEntry)read;
    assertNull(result.getId());
    assertNull(result.getName());

    List<Entry> children = result.getChildren();
    assertEquals(1, children.size());

    Entry e = result.findEntry("c:/root");
    assertNotNull(e);
    assertEquals(DirectoryEntry.class, e.getClass());

    e = result.findEntry("c:/root/file");
    assertNotNull(e);
    assertEquals(FileEntry.class, e.getClass());
  }

  @Test
  public void testCreateFileChange() throws IOException {
    Change c = new CreateFileChange(1, "file", "content", 777L);
    c.applyTo(new RootEntry());

    os.writeChange(c);
    Change read = is.readChange();

    assertEquals(CreateFileChange.class, read.getClass());
    CreateFileChange result = (CreateFileChange)read;

    assertEquals("file", result.getPath());
    assertElements(a(idp(1)), result.getAffectedIdPaths());

    assertEquals(1, result.getId());
    assertEquals("content", result.getContent());
    assertEquals(777L, result.getTimestamp());
  }

  @Test
  public void testCreateDirectoryChange() throws IOException {
    Change c = new CreateDirectoryChange(2, "dir", 333L);
    c.applyTo(new RootEntry());

    os.writeChange(c);
    Change read = is.readChange();

    assertEquals(CreateDirectoryChange.class, read.getClass());
    CreateDirectoryChange result = (CreateDirectoryChange)read;

    assertEquals("dir", result.getPath());
    assertElements(a(idp(2)), result.getAffectedIdPaths());

    assertEquals(2, result.getId());
    assertEquals(333L, result.getTimestamp());
  }

  @Test
  public void testChangeFileContentChange() throws IOException {
    RootEntry root = new RootEntry();
    root.createFile(1, "file", "old content", 1L);

    Change c = new ChangeFileContentChange("file", "new content", 2L);
    c.applyTo(root);

    os.writeChange(c);
    Change read = is.readChange();

    assertEquals(ChangeFileContentChange.class, read.getClass());
    ChangeFileContentChange result = (ChangeFileContentChange)read;

    assertEquals("file", result.getPath());
    assertElements(a(idp(1)), result.getAffectedIdPaths());

    assertEquals("new content", result.getNewContent());
    assertEquals(2L, result.getNewTimestamp());

    assertEquals("old content", result.getOldContent());
    assertEquals(1L, result.getOldTimestamp());
  }

  @Test
  public void testDeleteChange() throws IOException {
    RootEntry root = new RootEntry();

    root.createDirectory(1, "entry", null);
    root.createFile(2, "entry/file", "", null);
    root.createDirectory(3, "entry/dir", null);

    Change c = new DeleteChange("entry");
    c.applyTo(root);

    os.writeChange(c);
    Change read = is.readChange();

    assertEquals(DeleteChange.class, read.getClass());
    DeleteChange result = (DeleteChange)read;

    assertElements(a(idp(1)), result.getAffectedIdPaths());
    assertEquals("entry", result.getPath());

    Entry e = result.getAffectedEntry();

    assertEquals(DirectoryEntry.class, e.getClass());
    assertEquals("entry", e.getName());

    assertEquals(2, e.getChildren().size());
    assertEquals("entry/file", e.getChildren().get(0).getPath());
    assertEquals("entry/dir", e.getChildren().get(1).getPath());
  }

  @Test
  public void testRenameChange() throws IOException {
    RootEntry root = new RootEntry();
    root.createFile(1, "old name", null, null);

    Change c = new RenameChange("old name", "new name");
    c.applyTo(root);

    os.writeChange(c);
    Change read = is.readChange();

    assertEquals(RenameChange.class, read.getClass());
    RenameChange result = ((RenameChange)read);

    assertElements(a(idp(1)), result.getAffectedIdPaths());

    assertEquals("old name", result.getPath());
    assertEquals("new name", result.getNewName());
  }

  @Test
  public void testMoveChange() throws IOException {
    RootEntry root = new RootEntry();
    root.createDirectory(1, "dir1", null);
    root.createDirectory(2, "dir2", null);
    root.createFile(3, "dir1/file", null, null);

    Change c = new MoveChange("dir1/file", "dir2");
    c.applyTo(root);

    os.writeChange(c);
    Change read = is.readChange();

    assertEquals(MoveChange.class, read.getClass());
    MoveChange result = ((MoveChange)read);

    assertEquals("dir1/file", result.getPath());
    assertElements(a(idp(1, 3), idp(2, 3)), result.getAffectedIdPaths());

    assertEquals("dir2", result.getNewParentPath());
  }

  @Test
  public void testChangeSet() throws IOException {
    ChangeSet c = cs(new CreateFileChange(null, null, null, null));
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
    c.getChangeSets().add(cs(new CreateFileChange(null, null, null, null)));

    os.writeChangeList(c);
    ChangeList result = is.readChangeList();

    assertEquals(1, result.getChangeSets().size());
    assertEquals(1, result.getChangeSets().get(0).getChanges().size());
  }
}
