package com.intellij.localvcs.integration;

import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.TestCase;
import com.intellij.localvcs.TestStorage;
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
    root = new MyVirtualFile("root");
  }

  @Test
  public void testUpdatingRoot() throws IOException {
    // todo make this test a bit clearly
    vcs = new LocalVcs(new TestStorage());
    assertFalse(vcs.hasEntry("c:/root"));

    VirtualFile root = new MyVirtualFile("c:/root");
    Updater.update(vcs, root);

    assertTrue(vcs.hasEntry("c:/root"));
  }

  @Test
  public void testAddingNewFiles() throws IOException {
    MyVirtualFile dir = new MyVirtualFile("dir");
    MyVirtualFile file = new MyVirtualFile("file", "content");

    root.addChild(dir);
    dir.addChild(file);

    Updater.update(vcs, root);

    assertTrue(vcs.hasEntry("root/dir"));
    assertTrue(vcs.hasEntry("root/dir/file"));
    assertEquals("content", vcs.getEntry("root/dir/file").getContent());
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
    vcs.createDirectory("root/dir", null);
    vcs.createFile("root/dir/file", "content", null);
    vcs.apply();

    MyVirtualFile dir = new MyVirtualFile("dir");
    MyVirtualFile file = new MyVirtualFile("file", "content");

    root.addChild(dir);
    dir.addChild(file);

    Updater.update(vcs, root);

    assertTrue(vcs.hasEntry("root/dir"));
    assertTrue(vcs.hasEntry("root/dir/file"));
    assertEquals("content", vcs.getEntry("root/dir/file").getContent());
  }

  @Test
  public void testUpdatingOutdatedFiles() {
    vcs.createFile("file", "content", null);
    root.addChild(new MyVirtualFile("file", "new content"));

  }

  private class MyVirtualFile extends VirtualFile {
    private String myContent;
    private String myName;
    private boolean myIsDirectory;

    private VirtualFile myParent;
    private List<MyVirtualFile> myChildren = new ArrayList<MyVirtualFile>();

    public MyVirtualFile(String name, String content) {
      myName = name;
      myContent = content;
      myIsDirectory = false;
    }

    public MyVirtualFile(String name) {
      myName = name;
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

    public long getTimeStamp() {
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
