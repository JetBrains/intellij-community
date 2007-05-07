package com.intellij.localvcs.core.storage;

import com.intellij.localvcs.core.IdPath;
import com.intellij.localvcs.core.LocalVcsTestCase;
import com.intellij.localvcs.core.TestStorage;
import com.intellij.localvcs.core.changes.*;
import com.intellij.localvcs.core.tree.DirectoryEntry;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.core.tree.FileEntry;
import com.intellij.localvcs.core.tree.RootEntry;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;

public class StreamTest extends LocalVcsTestCase {
  private Stream is;
  private Stream os;
  private Storage s;

  @Before
  public void setUpStreams() throws IOException {
    PipedOutputStream pos = new PipedOutputStream();
    PipedInputStream pis = new PipedInputStream(pos);

    s = new TestStorage();
    os = new Stream(pos);
    is = new Stream(pis, s);
  }

  @Test
  public void testString() throws Exception {
    os.writeString("hello");
    assertEquals("hello", is.readString());
  }

  @Test
  public void testStringOrNull() throws Exception {
    os.writeStringOrNull("hello");
    os.writeStringOrNull(null);
    assertEquals("hello", is.readStringOrNull());
    assertNull(is.readStringOrNull());
  }

  @Test
  public void testInteger() throws Exception {
    os.writeInteger(1);
    assertEquals(1, is.readInteger());
  }

  @Test
  public void testLong() throws Exception {
    os.writeLong(1);
    assertEquals(1L, is.readLong());
  }

  @Test
  public void testBoolean() throws Exception {
    os.writeBoolean(true);
    assertTrue(is.readBoolean());
  }

  @Test
  public void testContent() throws Exception {
    Content c = s.storeContent(b("abc"));
    os.writeContent(c);

    assertEquals("abc".getBytes(), is.readContent().getBytes());
  }

  @Test
  public void testUnavailableContent() throws Exception {
    os.writeContent(new UnavailableContent());
    assertEquals(UnavailableContent.class, is.readContent().getClass());
  }

  @Test
  public void testDataAfterUnavailableContent() throws IOException {
    os.writeContent(new UnavailableContent());
    os.writeInteger(777);

    is.readContent();
    assertEquals(777, is.readInteger());
  }

  @Test
  public void testIdPath() throws Exception {
    IdPath p = new IdPath(1, 2, 3);
    os.writeIdPath(p);
    assertEquals(p, is.readIdPath());
  }

  @Test
  public void testFileEntry() throws Exception {
    Content c = s.storeContent(b("content"));
    Entry e = new FileEntry(42, "file", c, 123L);

    os.writeEntry(e);
    Entry result = is.readEntry();

    assertEquals(FileEntry.class, result.getClass());

    assertEquals(42, result.getId());
    assertEquals("file", result.getName());
    assertEquals("content".getBytes(), result.getContent().getBytes());
    assertEquals(123L, result.getTimestamp());
  }

  @Test
  public void testDoesNotWriteEntryParent() throws IOException {
    Entry parent = new DirectoryEntry(-1, "");
    Entry e = new FileEntry(42, "", new UnavailableContent(), -1);

    parent.addChild(e);
    os.writeEntry(e);

    assertNull(is.readEntry().getParent());
  }

  @Test
  public void testEmptyDirectoryEntry() throws IOException {
    Entry e = new DirectoryEntry(13, "name");

    os.writeEntry(e);
    Entry result = is.readEntry();

    assertEquals(DirectoryEntry.class, result.getClass());

    assertEquals(13, result.getId());
    assertEquals("name", result.getName());
  }

  @Test
  public void testDirectoryEntryWithChildren() throws IOException {
    Entry dir = new DirectoryEntry(1, "");
    Entry subDir = new DirectoryEntry(2, "");
    dir.addChild(subDir);
    subDir.addChild(new FileEntry(3, "a", new UnavailableContent(), -1));
    subDir.addChild(new FileEntry(4, "b", new UnavailableContent(), -1));

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
  public void testRootEntry() throws IOException {
    RootEntry r = new RootEntry();

    os.writeEntry(r);
    Entry read = is.readEntry();

    assertEquals(RootEntry.class, read.getClass());

    assertEquals(-1, read.getId());
    assertEquals("", read.getName());
  }

  @Test
  public void testRootEntryWithRootWithChildren() throws IOException {
    RootEntry r = new RootEntry();

    r.createDirectory(1, "c:/root");
    r.createFile(2, "c:/root/file", new UnavailableContent(), -1);

    os.writeEntry(r);
    Entry read = is.readEntry();

    List<Entry> children = read.getChildren();
    assertEquals(1, children.size());

    Entry e = read.findEntry("c:/root");
    assertNotNull(e);
    assertEquals(DirectoryEntry.class, e.getClass());

    e = read.findEntry("c:/root/file");
    assertNotNull(e);
    assertEquals(FileEntry.class, e.getClass());
  }

  @Test
  public void testCreateFileChange() throws IOException {
    Change c = new CreateFileChange(1, "file", s.storeContent(b("content")), 777L);
    c.applyTo(new RootEntry());

    os.writeChange(c);
    Change read = is.readChange();

    assertEquals(CreateFileChange.class, read.getClass());
    CreateFileChange result = (CreateFileChange)read;

    assertEquals(a(idp(1)), result.getAffectedIdPaths());
  }

  @Test
  public void testCreateDirectoryChange() throws IOException {
    Change c = new CreateDirectoryChange(2, "dir");
    c.applyTo(new RootEntry());

    os.writeChange(c);
    Change read = is.readChange();

    assertEquals(CreateDirectoryChange.class, read.getClass());
    CreateDirectoryChange result = (CreateDirectoryChange)read;

    assertEquals(a(idp(2)), result.getAffectedIdPaths());
  }

  @Test
  public void testChangeFileContentChange() throws IOException {
    RootEntry root = new RootEntry();
    root.createFile(1, "file", s.storeContent(b("old content")), 1L);

    Change c = new ChangeFileContentChange("file", s.storeContent(b("new content")), 2L);
    c.applyTo(root);

    os.writeChange(c);
    Change read = is.readChange();

    assertEquals(ChangeFileContentChange.class, read.getClass());
    ChangeFileContentChange result = (ChangeFileContentChange)read;

    assertEquals(a(idp(1)), result.getAffectedIdPaths());
    assertEquals(c("old content"), result.getOldContent());
    assertEquals(1L, result.getOldTimestamp());
  }

  @Test
  public void testDeleteChange() throws IOException {
    RootEntry root = new RootEntry();

    root.createDirectory(1, "entry");
    root.createFile(2, "entry/file", new UnavailableContent(), -1);
    root.createDirectory(3, "entry/dir");

    Change c = new DeleteChange("entry");
    c.applyTo(root);

    os.writeChange(c);
    Change read = is.readChange();

    assertEquals(DeleteChange.class, read.getClass());
    DeleteChange result = (DeleteChange)read;

    assertEquals(a(idp(1)), result.getAffectedIdPaths());

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
    root.createFile(1, "old name", null, -1);

    Change c = new RenameChange("old name", "new name");
    c.applyTo(root);

    os.writeChange(c);
    Change read = is.readChange();

    assertEquals(RenameChange.class, read.getClass());
    RenameChange result = ((RenameChange)read);

    assertEquals(a(idp(1)), result.getAffectedIdPaths());
    assertEquals("old name", result.getOldName());
  }

  @Test
  public void testMoveChange() throws IOException {
    RootEntry root = new RootEntry();
    root.createDirectory(1, "dir1");
    root.createDirectory(2, "dir2");
    root.createFile(3, "dir1/file", null, -1);

    Change c = new MoveChange("dir1/file", "dir2");
    c.applyTo(root);

    os.writeChange(c);
    Change read = is.readChange();

    assertEquals(MoveChange.class, read.getClass());
    MoveChange result = ((MoveChange)read);

    assertEquals(a(idp(1, 3), idp(2, 3)), result.getAffectedIdPaths());
  }

  @Test
  public void testPutLabelChange() throws IOException {
    RootEntry r = new RootEntry();
    r.createDirectory(1, "dir");

    Change c = new PutLabelChange(123, "name", true);
    c.applyTo(r);

    os.writeChange(c);
    Change read = is.readChange();

    assertEquals(PutLabelChange.class, read.getClass());
    assertEquals(123, read.getTimestamp());
    assertEquals("name", read.getName());
    assertTrue(((PutLabelChange)read).isMark());
    assertTrue(read.affects(r.getEntry("dir")));
  }

  @Test
  public void testPutEntryLabelChange() throws IOException {
    RootEntry r = new RootEntry();
    r.createDirectory(1, "dir");

    Change c = new PutEntryLabelChange(123, "dir", "name", false);
    c.applyTo(r);

    os.writeChange(c);
    Change read = is.readChange();

    assertEquals(PutEntryLabelChange.class, read.getClass());
    assertEquals(123, read.getTimestamp());
    assertEquals("name", read.getName());
    assertFalse(((PutLabelChange)read).isMark());
    assertTrue(read.affects(r.getEntry("dir")));
  }

  @Test
  public void testChangeSet() throws IOException {
    ChangeSet cs = cs(123, "name", new CreateFileChange(1, "file", new UnavailableContent(), -1));

    cs.applyTo(new RootEntry());

    os.writeChange(cs);
    Change read = is.readChange();
    assertTrue(read.getClass().equals(ChangeSet.class));

    ChangeSet result = (ChangeSet)read;

    assertEquals("name", read.getName());
    assertEquals(123L, read.getTimestamp());
    assertEquals(1, result.getChanges().size());
    assertEquals(CreateFileChange.class, result.getChanges().get(0).getClass());
  }

  @Test
  public void testChangeSetWithoutName() throws IOException {
    ChangeSet cs = cs((String)null);

    os.writeChange(cs);
    Change result = is.readChange();

    assertNull(result.getName());
  }

  @Test
  public void testEmptyChangeList() throws IOException {
    ChangeList c = new ChangeList();

    os.writeChangeList(c);
    ChangeList result = is.readChangeList();

    assertTrue(result.getChanges().isEmpty());
  }

  @Test
  public void testChangeList() throws IOException {
    ChangeList c = new ChangeList();
    ChangeSet cs = cs(new CreateFileChange(1, "file", new UnavailableContent(), -1));
    cs.applyTo(new RootEntry());
    c.addChange(cs);

    os.writeChangeList(c);
    ChangeList result = is.readChangeList();

    assertEquals(1, result.getChanges().size());
    assertEquals(1, ((ChangeSet)result.getChanges().get(0)).getChanges().size());
  }
}
