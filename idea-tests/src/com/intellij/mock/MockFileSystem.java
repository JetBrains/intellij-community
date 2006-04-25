package com.intellij.mock;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightVirtualFile;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;

import org.jetbrains.annotations.NotNull;

public class MockFileSystem extends VirtualFileSystem {
  private final MyVirtualFile myRoot = new MyVirtualFile("", null);
  public static final String PROTOCOL = "mock";

  public VirtualFile findFileByPath(String path) {
    path = path.replace(File.separatorChar, '/');
    path = path.replace('/', ':');
    if (StringUtil.startsWithChar(path, ':')) path = path.substring(1);
    String[] components = path.split(":");
    MyVirtualFile file = myRoot;
    for (String component : components) {
      file = file.getOrCreate(component);
    }
    return file;
  }

  public String getProtocol() {
    return PROTOCOL;
  }

  public void refresh(boolean asynchronous) {
  }

  public void forceRefreshFiles(final boolean asynchronous, VirtualFile... files) {

  }

  public void deleteFile(Object requestor, VirtualFile vFile) throws IOException {
  }

  public void moveFile(Object requestor, VirtualFile vFile, VirtualFile newParent) throws IOException {
  }

  public void renameFile(Object requestor, VirtualFile vFile, String newName) throws IOException {
  }

  public VirtualFile createChildFile(Object requestor, VirtualFile vDir, String fileName) throws IOException {
    return null;
  }

  public VirtualFile createChildDirectory(Object requestor, VirtualFile vDir, String dirName) throws IOException {
    return null;
  }

  public VirtualFile refreshAndFindFileByPath(String path) {
    return findFileByPath(path);
  }

  public class MyVirtualFile extends LightVirtualFile {
    private final HashMap<String, MyVirtualFile> myChildren = new HashMap<String,MyVirtualFile>();
    private final MyVirtualFile myParent;

    public MyVirtualFile(String name, MyVirtualFile parent) {
      super(name);
      myParent = parent;
    }

    @NotNull
    public VirtualFileSystem getFileSystem() {
      return MockFileSystem.this;
    }

    public MyVirtualFile getOrCreate(String name) {
      MyVirtualFile file = myChildren.get(name);
      if (file == null) {
        file = new MyVirtualFile(name, this);
        myChildren.put(name, file);
      }
      return file;
    }

    public boolean isDirectory() {
      return myChildren.size() != 0;
    }

    public String getPath() {
      final MockFileSystem.MyVirtualFile parent = getParent();
      return parent == null ? getName() : parent.getPath() + "/" + getName();
    }

    public MyVirtualFile getParent() { return myParent; }

    public VirtualFile[] getChildren() {
      Collection<MyVirtualFile> children = myChildren.values();
      return children.toArray(new MyVirtualFile[children.size()]);
    }

    public String toString() {
      return getUrl();
    }
  }
}
