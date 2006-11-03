package com.intellij.localvcs;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class SerializationTest extends TestCase {
  // todo replace DataStreams with ObjectStreams and remove checks for null
  private DataOutputStream os;
  private DataInputStream is;

  @Before
  public void setUpStreams() throws IOException {
    PipedOutputStream pos = new PipedOutputStream();
    PipedInputStream pis = new PipedInputStream(pos);

    os = new DataOutputStream(pos);
    is = new DataInputStream(pis);
  }

  @Test
  public void testPath() throws IOException {
    Path p = new Path("dir/file");
    p.write(os);
    assertEquals(p, new Path(is));
  }

  @Test
  public void testFileEntry() throws IOException {
    Entry e = new FileEntry(42, "file", "content");
    e.write(os);

    Entry result = Entry.read(is);

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
    e.write(os);

    assertNull(Entry.read(is).getParent());
  }

  @Test
  public void tesEmptyDirectoryEntry() throws IOException {
    Entry e = new DirectoryEntry(13, "name");
    e.write(os);

    Entry result = Entry.read(is);

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

    dir.write(os);

    Entry result = Entry.read(is);
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

    e.write(os);

    // todo maby we should read RootEntry with Entry.read() too?
    Entry result = new RootEntry(is);

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
    c.write(os);

    Change result = Change.read(is);
    assertEquals(CreateFileChange.class, result.getClass());

    assertEquals(p("file"), ((CreateFileChange)result).getPath());
    assertEquals("content", ((CreateFileChange)result).getContent());
  }

  @Test
  public void testCreateDirectoryChange() throws IOException {
    Change c = new CreateDirectoryChange(p("dir"));
    c.write(os);

    Change result = Change.read(is);
    assertEquals(CreateDirectoryChange.class, result.getClass());
    assertEquals(p("dir"), ((CreateDirectoryChange)result).getPath());
  }

  @Test
  public void testDeleteChange() throws IOException {
    Change c = new DeleteChange(p("entry"));
    c.write(os);

    Change result = Change.read(is);
    assertEquals(DeleteChange.class, result.getClass());

    assertEquals(p("entry"), ((DeleteChange)result).getPath());
    assertNull(((DeleteChange)result).getAffectedEntry());
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

    c.write(os);

    Entry result = ((DeleteChange)Change.read(is)).getAffectedEntry();

    assertEquals(DirectoryEntry.class, result.getClass());
    assertEquals("entry", result.getName());

    assertEquals(2, result.getChildren().size());
    assertEquals("file", result.getChildren().get(0).getName());
    assertEquals("dir", result.getChildren().get(1).getName());
  }
}
