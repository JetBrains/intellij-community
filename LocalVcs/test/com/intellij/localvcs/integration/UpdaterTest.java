package com.intellij.localvcs.integration;

import com.intellij.localvcs.Entry;
import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.TestStorage;
import com.intellij.localvcs.TestCase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class UpdaterTest extends TestCase {
  private LocalVcs vcs;
  private MyVirtualFile root;

  @Before
  public void setUp() {
    vcs = new LocalVcs(new TestStorage());
    vcs.getRoot().setPath("root");
    root = new MyVirtualFile("root", 0);
  }

  @Test
  public void testUpdatingRoot() throws IOException {
    vcs = new LocalVcs(new TestStorage());
    assertFalse(vcs.hasEntry("c:/root"));

    VirtualFile root = new MyVirtualFile("c:/root", 0);
    Updater.update(vcs, root);

    // todo make this assertion a bit more explicit
    assertTrue(vcs.hasEntry("c:/root"));
  }

  @Test
  public void testAddingNewFiles() throws IOException {
    MyVirtualFile dir = new MyVirtualFile("dir", 1L);
    MyVirtualFile file = new MyVirtualFile("file", "content", 2L);

    root.addChild(dir);
    dir.addChild(file);

    Updater.update(vcs, root);

    assertTrue(vcs.hasEntry("root/dir"));
    assertTrue(vcs.hasEntry("root/dir/file"));

    assertEquals(1L, vcs.getEntry("root/dir").getTimestamp());

    Entry e = vcs.getEntry("root/dir/file");
    assertEquals("content", e.getContent());
    assertEquals(2L, e.getTimestamp());
  }

  @Test
  public void testDeletingAbsentFiles() throws IOException {
    vcs.createFile("root/file", null, null);
    vcs.createDirectory("root/dir", null);
    vcs.createFile("root/dir/file", null, null);
    vcs.apply();

    assertTrue(vcs.hasEntry("root/file"));
    assertTrue(vcs.hasEntry("root/dir/file"));

    Updater.update(vcs, root);

    assertFalse(vcs.hasEntry("root/file"));
    assertFalse(vcs.hasEntry("root/dir"));
    assertFalse(vcs.hasEntry("root/dir/file"));
  }

  @Test
  public void testDoesNothingWithUnchangedEntries() throws IOException {
    vcs.createDirectory("root/dir", 1L);
    vcs.createFile("root/dir/file", "content", 1L);
    vcs.apply();

    MyVirtualFile dir = new MyVirtualFile("dir", 1L);
    MyVirtualFile file = new MyVirtualFile("file", "content", 1L);

    root.addChild(dir);
    dir.addChild(file);

    Updater.update(vcs, root);

    assertTrue(vcs.hasEntry("root/dir"));
    assertTrue(vcs.hasEntry("root/dir/file"));
    assertEquals("content", vcs.getEntry("root/dir/file").getContent());
  }

  @Test
  public void testUpdatingOutdatedFiles() throws IOException {
    vcs.createFile("root/file", "content", 111L);
    vcs.apply();

    root.addChild(new MyVirtualFile("file", "new content", 222L));

    Updater.update(vcs, root);

    Entry e = vcs.getEntry("root/file");

    assertEquals("new content", e.getContent());
    assertEquals(222L, e.getTimestamp());
  }

  private class MyVirtualFile extends VirtualFile {
    private String myName;
    private String myContent;
    private long myTimestamp;

    private boolean myIsDirectory;
    private VirtualFile myParent;
    private List<MyVirtualFile> myChildren = new ArrayList<MyVirtualFile>();

    public MyVirtualFile(String name, String content, long timestamp) {
      myName = name;
      myContent = content;
      myTimestamp = timestamp;
      myIsDirectory = false;
    }

    public MyVirtualFile(String name, long timestamp) {
      myName = name;
      myTimestamp = timestamp;
      myIsDirectory = true;
    }

    public String getName() {
      return myName;
    }

    public boolean isDirectory() {
      return myIsDirectory;
    }

    public String getPath() {
      if (myParent == null) return myName;
      return myParent.getPath() + "/" + myName;
    }

    public long getTimeStamp() {
      return myTimestamp;
    }

    public VirtualFile[] getChildren() {
      return myChildren.toArray(new VirtualFile[0]);
    }

    public void addChild(MyVirtualFile f) {
      f.myParent = this;
      myChildren.add(f);
    }

    public byte[] contentsToByteArray() throws IOException {
      return myContent.getBytes();
    }

    @NotNull
    public VirtualFileSystem getFileSystem() {
      throw new UnsupportedOperationException();
    }

    public boolean isWritable() {
      throw new UnsupportedOperationException();
    }

    public boolean isValid() {
      throw new UnsupportedOperationException();
    }

    @Nullable
    public VirtualFile getParent() {
      throw new UnsupportedOperationException();
    }

    public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
      throw new UnsupportedOperationException();
    }

    public long getLength() {
      throw new UnsupportedOperationException();
    }

    public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
      throw new UnsupportedOperationException();
    }

    public InputStream getInputStream() throws IOException {
      throw new UnsupportedOperationException();
    }
  }
}
