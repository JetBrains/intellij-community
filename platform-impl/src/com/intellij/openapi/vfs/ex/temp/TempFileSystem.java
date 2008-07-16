/*
 * @author max
 */
package com.intellij.openapi.vfs.ex.temp;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class TempFileSystem extends NewVirtualFileSystem {
  private FSItem myRoot = new FSDir(null, "/");

  public static TempFileSystem getInstance() {
    return ApplicationManager.getApplication().getComponent(TempFileSystem.class);
  }

  public boolean isCaseSensitive() {
    return true;
  }

  protected String extractRootPath(@NotNull final String path) {
    return path.startsWith("/") ? "/" : "";
  }

  public int getRank() {
    return 1;
  }

  @Nullable
  private FSItem convert(VirtualFile file) {
    final VirtualFile parentFile = file.getParent();
    if (parentFile == null) return myRoot;
    FSItem parentItem = convert(parentFile);
    if (parentItem == null || !parentItem.isDirectory()) {
      return null;
    }

    return parentItem.findChild(file.getName());
  }

  public VirtualFile copyFile(final Object requestor, final VirtualFile file, final VirtualFile newParent, final String copyName)
      throws IOException {
    throw new UnsupportedOperationException("copyFile is not implemented"); // TODO
  }

  public VirtualFile createChildDirectory(final Object requestor, final VirtualFile parent, final String dir) throws IOException {
    final FSItem fsItem = convert(parent);
    assert fsItem != null && fsItem.isDirectory();

    final FSDir fsDir = (FSDir)fsItem;
    fsDir.addChild(new FSDir(fsDir, dir));

    return new FakeVirtualFile(dir, parent);
  }

  public VirtualFile createChildFile(final Object requestor, final VirtualFile parent, final String file) throws IOException {
    final FSItem fsItem = convert(parent);
    assert fsItem != null && fsItem.isDirectory();

    final FSDir fsDir = (FSDir)fsItem;
    fsDir.addChild(new FSFile(fsDir, file));

    return new FakeVirtualFile(file, parent);
  }

  public void deleteFile(final Object requestor, final VirtualFile file) throws IOException {
    final FSItem fsItem = convert(file);
    assert fsItem != null;

    fsItem.getParent().removeChild(fsItem);
  }

  public void moveFile(final Object requestor, final VirtualFile file, final VirtualFile newParent) throws IOException {
    throw new UnsupportedOperationException("moveFile is not implemented"); // TODO
  }

  public void renameFile(final Object requestor, final VirtualFile file, final String newName) throws IOException {
    final FSItem fsItem = convert(file);
    assert fsItem != null;

    fsItem.setName(newName);
  }

  public String getProtocol() {
    return "temp";
  }

  public boolean exists(final VirtualFile fileOrDirectory) {
    return convert(fileOrDirectory) != null;
  }

  public String[] list(final VirtualFile file) {
    final FSItem fsItem = convert(file);
    assert fsItem != null;

    return fsItem.list();
  }

  public boolean isDirectory(final VirtualFile file) {
    return convert(file) instanceof FSDir;
  }

  public long getTimeStamp(final VirtualFile file) {
    final FSItem fsItem = convert(file);
    assert fsItem != null;

    return fsItem.myTimestamp;
  }

  public void setTimeStamp(final VirtualFile file, final long modstamp) throws IOException {
    final FSItem fsItem = convert(file);
    assert fsItem != null;

    fsItem.myTimestamp = modstamp > 0 ? modstamp : LocalTimeCounter.currentTime();
  }

  public boolean isWritable(final VirtualFile file) {
    final FSItem fsItem = convert(file);
    assert fsItem != null;

    return fsItem.myWritable;
  }

  public void setWritable(final VirtualFile file, final boolean writableFlag) throws IOException {
    final FSItem fsItem = convert(file);
    assert fsItem != null;

    fsItem.myWritable = writableFlag;
  }

  public byte[] contentsToByteArray(final VirtualFile file) throws IOException {
    final FSItem fsItem = convert(file);
    if (fsItem == null) throw new FileNotFoundException("Cannot find temp for " + file.getPath());
    
    assert fsItem instanceof FSFile;

    return ((FSFile)fsItem).myContent;
  }

  public InputStream getInputStream(final VirtualFile file) throws IOException {
    return new ByteArrayInputStream(contentsToByteArray(file));
  }

  public OutputStream getOutputStream(final VirtualFile file, final Object requestor, final long modStamp, final long timeStamp)
      throws IOException {
    return new ByteArrayOutputStream() {
      public void close() throws IOException {
        super.close();
        final FSItem fsItem = convert(file);
        assert fsItem instanceof FSFile;

        ((FSFile)fsItem).myContent = toByteArray();
        setTimeStamp(file, modStamp);
      }
    };
  }

  public long getLength(final VirtualFile file) {
    try {
      return contentsToByteArray(file).length;
    }
    catch (IOException e) {
      return 0;
    }
  }

  private static abstract class FSItem {
    private FSDir myParent;
    private String myName;
    private long myTimestamp;
    private boolean myWritable;

    protected FSItem(final FSDir parent, final String name) {
      myParent = parent;
      myName = name;
      myTimestamp = LocalTimeCounter.currentTime();
      myWritable = true;
    }

    public abstract boolean isDirectory();

    @Nullable
    public FSItem findChild(final String name) {
      return null;
    }

    public void setName(final String name) {
      myName = name;
    }

    public FSDir getParent() {
      return myParent;
    }

    public String[] list() {
      return new String[0];
    }
  }

  private static class FSDir extends FSItem {
    private final List<FSItem> myChildren = new ArrayList<FSItem>();

    public FSDir(final FSDir parent, final String name) {
      super(parent, name);
    }

    @Nullable
    public FSItem findChild(final String name) {
      for (FSItem child : myChildren) {
        if (name.equals(child.myName)) {
          return child;
        }
      }

      return null;
    }

    public boolean isDirectory() {
      return true;
    }

    public void addChild(final FSItem item) {
      myChildren.add(item);
    }

    public void removeChild(final FSItem fsItem) {
      myChildren.remove(fsItem);
    }

    public String[] list() {
      String[] names = new String[myChildren.size()];
      for (int i = 0; i < names.length; i++) {
        names[i] = myChildren.get(i).myName;
      }
      return names;
    }
  }

  private static class FSFile extends FSItem {
    public FSFile(final FSDir parent, final String name) {
      super(parent, name);
    }

    private byte[] myContent = new byte[0];

    public boolean isDirectory() {
      return false;
    }
  }
}