package com.intellij.openapi.vfs.ex.dummy;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem;
import com.intellij.openapi.vfs.VfsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 *
 */
public class DummyFileSystem extends DeprecatedVirtualFileSystem implements ApplicationComponent {
  @NonNls public static final String PROTOCOL = "dummy";
  private VirtualFileDirectoryImpl myRoot;

  public static DummyFileSystem getInstance() {
    return ApplicationManager.getApplication().getComponent(DummyFileSystem.class);
  }

  public DummyFileSystem() {
  }

  public void disposeComponent() {
  }

  public void initComponent() {
  }

  public VirtualFile createRoot(String name) {
    myRoot = new VirtualFileDirectoryImpl(this, null, name);
    fireFileCreated(null, myRoot);
    return myRoot;
  }

  @Nullable
  public VirtualFile findById(int id) {
    return findById(id, myRoot);
  }

  @Nullable
  private static VirtualFile findById(final int id, final VirtualFileImpl r) {
    if (r.getId() == id) return r;
    for (VirtualFile f : r.getChildren()) {
      final VirtualFile child = findById(id, (VirtualFileImpl)f);
      if (child != null) return child;
    }
    return null;
  }

  public String getProtocol() {
    return PROTOCOL;
  }

  public VirtualFile findFileByPath(@NotNull String path) {
//    LOG.error("method not implemented");
    return null;
  }

  public String extractPresentableUrl(String path) {
    return path;
  }

  public void refresh(boolean asynchronous) {
  }

  public VirtualFile refreshAndFindFileByPath(String path) {
    return findFileByPath(path);
  }

  @NotNull
  public String getComponentName() {
    return "DummyFileSystem";
  }

  public void deleteFile(Object requestor, VirtualFile vFile) throws IOException {
    fireBeforeFileDeletion(requestor, vFile);
    final VirtualFileDirectoryImpl parent = (VirtualFileDirectoryImpl)vFile.getParent();
    if (parent == null) {
      throw new IOException(VfsBundle.message("file.delete.root.error", vFile.getPresentableUrl()));
    }

    parent.removeChild((VirtualFileImpl)vFile);
    fireFileDeleted(requestor, vFile, vFile.getName(), parent);
  }

  public void moveFile(Object requestor, VirtualFile vFile, VirtualFile newParent) throws IOException {
    throw new UnsupportedOperationException();
  }

  public VirtualFile copyFile(Object requestor, VirtualFile vFile, VirtualFile newParent, final String copyName) throws IOException {
    throw new UnsupportedOperationException();
  }

  public void renameFile(Object requestor, VirtualFile vFile, String newName) throws IOException {
    final String oldName = vFile.getName();
    fireBeforePropertyChange(requestor, vFile, VirtualFile.PROP_NAME, oldName, newName);
    ((VirtualFileImpl)vFile).setName(newName);
    firePropertyChanged(requestor, vFile, VirtualFile.PROP_NAME, oldName, newName);
  }

  public VirtualFile createChildFile(Object requestor, VirtualFile vDir, String fileName) throws IOException {
    final VirtualFileDirectoryImpl dir = ((VirtualFileDirectoryImpl)vDir);
    VirtualFileImpl child = new VirtualFileDataImpl(this, dir, fileName);
    dir.addChild(child);
    fireFileCreated(requestor, child);
    return child;
  }

  @Override
  public void fireBeforeContentsChange(final Object requestor, final VirtualFile file) {
    super.fireBeforeContentsChange(requestor, file);
  }

  @Override
  public void fireContentsChanged(final Object requestor, final VirtualFile file, final long oldModificationStamp) {
    super.fireContentsChanged(requestor, file, oldModificationStamp);
  }

  public VirtualFile createChildDirectory(Object requestor, VirtualFile vDir, String dirName) throws IOException {
    final VirtualFileDirectoryImpl dir = ((VirtualFileDirectoryImpl)vDir);
    VirtualFileImpl child = new VirtualFileDirectoryImpl(this, dir, dirName);
    dir.addChild(child);
    fireFileCreated(requestor, child);
    return child;
  }
}
