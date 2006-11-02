package com.intellij.localvcs;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import org.junit.Before;
import org.junit.Test;

public class SerializationTest extends TestCase {
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
    FileEntry e = new FileEntry(42, "file", "content");
    e.write(os);
    assertEquals(e, Entry.read(is));
  }

  @Test
  public void testDoesNotWriteEntryParent() throws IOException {
    DirectoryEntry parent = new DirectoryEntry(null, null);
    FileEntry e = new FileEntry(42, "file", "content");
    parent.addChild(e);
    e.write(os);

    assertNull(Entry.read(is).getParent());
  }

  @Test
  public void tesEmptyDirectoryEntry() throws IOException {
    DirectoryEntry e = new DirectoryEntry(13, "name");
    e.write(os);

    Entry result = Entry.read(is);

    assertEquals(DirectoryEntry.class, result.getClass());
    assertEquals(e, result);
  }

  @Test
  public void testDirectoryEntryWithChildren() throws IOException {
    DirectoryEntry e = new DirectoryEntry(13, "name");
    Entry c1 = new FileEntry(1, "c1", "1");
    Entry c2 = new DirectoryEntry(2, "c2");
    Entry c3 = new FileEntry(3, "c3", "3");

    e.addChild(c1);
    e.addChild(c2);
    c2.addChild(c3);

    e.write(os);

    assertEquals(e, Entry.read(is));
  }

  @Test
  public void testRootEntryWithChildren() throws IOException {
    RootEntry e = new RootEntry();
    Entry c1 = new FileEntry(1, "c1", "1");
    Entry c2 = new DirectoryEntry(2, "c2");
    Entry c3 = new FileEntry(3, "c3", "3");

    e.addChild(c1);
    e.addChild(c2);
    c2.addChild(c3);

    e.write(os);

    // todo bad... it seems that something wrong with RootEntry
    Entry result = new RootEntry(is);

    assertEquals(RootEntry.class, result.getClass());
    assertNull(result.getObjectId());
    assertNull(result.getName());
    assertEquals(e.getChildren(), result.getChildren());
  }

  @Test
  public void testCreateFileChange() throws IOException {
    Change c = new CreateFileChange(p("file"), "content");
    c.write(os);
    assertEquals(c, Change.read(is));
  }

  @Test
  public void testCreateDirectoryChange() throws IOException {
    Change c = new CreateDirectoryChange(p("dir"));
    c.write(os);
    assertEquals(c, Change.read(is));
  }
}
