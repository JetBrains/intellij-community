package com.intellij.localvcs;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

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
  public void testFileEntry() throws IOException {
    Entry e = new FileEntry(42, "file", "content");

    os.writeEntry(e);
    Entry result = is.readEntry();

    assertEquals(FileEntry.class, result.getClass());
    assertEquals(42, result.getObjectId());
    assertEquals("file", result.getName());
    assertEquals("content", result.getContent());
  }

  @Test
  public void testDoesNotWriteEntryParent() throws IOException {
    Entry parent = new DirectoryEntry(null, null);
    Entry e = new FileEntry(42, "file", "content");

    parent.addChild(e);
    os.writeEntry(e);

    assertNull(is.readEntry().getParent());
  }

  @Test
  public void tesEmptyDirectoryEntry() throws IOException {
    Entry e = new DirectoryEntry(13, "name");

    os.writeEntry(e);
    Entry result = is.readEntry();

    assertEquals(DirectoryEntry.class, result.getClass());
    assertEquals(13, result.getObjectId());
    assertEquals("name", result.getName());
  }

  @Test
  public void testDirectoryEntryWithChildren() throws IOException {
    Entry dir = new DirectoryEntry(13, "dir");
    Entry subDir = new DirectoryEntry(66, "subdir");

    dir.addChild(new FileEntry(1, "f1", "1"));
    dir.addChild(subDir);
    subDir.addChild(new FileEntry(2, "f2", "2"));

    os.writeEntry(dir);
    Entry result = is.readEntry();

    List<Entry> children = result.getChildren();
    assertEquals(2, children.size());

    assertEquals(FileEntry.class, children.get(0).getClass());
    assertEquals("f1", children.get(0).getName());

    assertEquals(DirectoryEntry.class, children.get(1).getClass());
    assertEquals("subdir", children.get(1).getName());
    assertEquals(1, children.get(1).getChildren().size());
    assertEquals("f2", children.get(1).getChildren().get(0).getName());
  }

  @Test
  public void testRootEntryWithChildren() throws IOException {
    RootEntry e = new RootEntry();
    e.addChild(new FileEntry(1, "file", ""));
    e.addChild(new DirectoryEntry(2, "dir"));

    os.writeEntry(e);
    Entry result = is.readEntry();

    assertEquals(RootEntry.class, result.getClass());
    assertNull(result.getObjectId());
    assertNull(result.getName());

    List<Entry> children = result.getChildren();
    assertEquals(2, children.size());

    assertEquals(FileEntry.class, children.get(0).getClass());
    assertEquals("file", children.get(0).getName());

    assertEquals(DirectoryEntry.class, children.get(1).getClass());
    assertEquals("dir", children.get(1).getName());
  }

  @Test
  public void testCreateFileChange() throws IOException {
    Change c = new CreateFileChange(p("file"), "content");

    os.writeChange(c);
    Change result = is.readChange();

    assertEquals(CreateFileChange.class, result.getClass());

    assertEquals(p("file"), ((CreateFileChange)result).getPath());
    assertEquals("content", ((CreateFileChange)result).getContent());
  }

  @Test
  public void testCreateDirectoryChange() throws IOException {
    Change c = new CreateDirectoryChange(p("dir"));

    os.writeChange(c);
    Change result = is.readChange();

    assertEquals(CreateDirectoryChange.class, result.getClass());
    assertEquals(p("dir"), ((CreateDirectoryChange)result).getPath());
  }

  @Test
  public void testDeleteChange() throws IOException {
    Change c = new DeleteChange(p("entry"));

    os.writeChange(c);
    Change read = is.readChange();

    assertEquals(DeleteChange.class, read.getClass());

    DeleteChange result = ((DeleteChange)read);

    assertEquals(p("entry"), result.getPath());
    assertNull(result.getAffectedEntry());
  }

  @Test
  public void testAppliedDeleteChange() throws IOException {
    Change c = new DeleteChange(p("entry"));

    c.applyTo(new Snapshot() {
      @Override
      public Entry getEntry(Path path) {
        Entry e = new DirectoryEntry(1, "entry");
        e.addChild(new FileEntry(2, "file", ""));
        e.addChild(new DirectoryEntry(3, "dir"));
        return e;
      }
    });

    os.writeChange(c);
    Entry result = ((DeleteChange)is.readChange()).getAffectedEntry();

    assertEquals(DirectoryEntry.class, result.getClass());
    assertEquals("entry", result.getName());

    assertEquals(2, result.getChildren().size());
    assertEquals("file", result.getChildren().get(0).getName());
    assertEquals("dir", result.getChildren().get(1).getName());
  }

  @Test
  public void testChangeFileContentChange() throws IOException {
    Change c = new ChangeFileContentChange(p("entry"), "new content");

    os.writeChange(c);
    Change read = is.readChange();

    assertEquals(ChangeFileContentChange.class, read.getClass());

    ChangeFileContentChange result = ((ChangeFileContentChange)read);

    assertEquals(p("entry"), result.getPath());
    assertEquals("new content", result.getNewContent());
    assertNull(result.getOldContent());
  }

  @Test
  public void testAppliedChangeFileContentChange() throws IOException {
    Change c = new ChangeFileContentChange(p("file"), "new content");

    c.applyTo(new Snapshot() {
      @Override
      public Entry getEntry(Path path) {
        return new FileEntry(null, null, "content");
      }
    });

    os.writeChange(c);
    Change read = is.readChange();

    assertEquals("content", ((ChangeFileContentChange)read).getOldContent());
  }

  @Test
  public void testRenameChange() throws IOException {
    Change c = new RenameChange(p("entry"), "new name");

    os.writeChange(c);
    Change read = is.readChange();

    assertEquals(RenameChange.class, read.getClass());

    RenameChange result = ((RenameChange)read);

    assertEquals(p("entry"), result.getPath());
    assertEquals("new name", result.getNewName());
  }

  @Test
  public void testMoveChange() throws IOException {
    Change c = new MoveChange(p("entry"), p("dir"));

    os.writeChange(c);
    Change read = is.readChange();

    assertEquals(MoveChange.class, read.getClass());

    MoveChange result = ((MoveChange)read);

    assertEquals(p("entry"), result.getPath());
    assertEquals(p("dir"), result.getNewParent());
  }

  @Test
  public void testChangeSet() throws IOException {
    ChangeSet c = cs(new CreateFileChange(p(""), ""));
    c.setLabel("label");

    os.writeChangeSet(c);
    ChangeSet result = is.readChangeSet();

    assertEquals("label", result.getLabel());
    assertEquals(1, result.getChanges().size());
    assertEquals(CreateFileChange.class, result.getChanges().get(0).getClass());
  }
}
