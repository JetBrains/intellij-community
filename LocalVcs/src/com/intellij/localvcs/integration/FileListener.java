package com.intellij.localvcs.integration;

import com.intellij.localvcs.ILocalVcs;
import com.intellij.localvcs.Paths;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileListener extends VirtualFileAdapter implements VirtualFileManagerListener, CommandListener {
  private ILocalVcs myVcs;
  private FileFilter myFileFilter;
  private ServiceState myState;

  public FileListener(ILocalVcs vcs, IdeaGateway gw, FileFilter f) {
    myVcs = vcs;
    myFileFilter = f;
    goToState(new ListeningServiceState(this, vcs, gw));
  }

  public void beforeRefreshStart(boolean asynchonous) {
    myState.startRefreshing();
  }

  public void afterRefreshFinish(boolean asynchonous) {
    myState.finishRefreshing();
  }

  public void commandStarted(CommandEvent e) {
    myState.startCommand();
  }

  public void commandFinished(CommandEvent e) {
    myState.finishCommand();
  }

  protected void goToState(ServiceState s) {
    myState = s;
  }

  public boolean isFileContentChangedByRefresh(VirtualFile f) {
    return myState.isFileContentChangedByRefresh(f);
  }

  @Override
  public void fileCreated(VirtualFileEvent e) {
    if (notAllowedOrNotUnderContentRoot(e)) return;
    myState.create(e.getFile());
  }

  @Override
  public void contentsChanged(VirtualFileEvent e) {
    if (notAllowedOrNotUnderContentRoot(e)) return;
    myState.changeFileContent(e.getFile());
  }

  @Override
  public void propertyChanged(VirtualFilePropertyEvent e) {
    if (!e.getPropertyName().equals(VirtualFile.PROP_NAME)) return;

    VirtualFile newFile = e.getFile();
    VirtualFile oldFile = new RenamedVirtualFile(e.getFile(), (String)e.getOldValue());
    boolean wasInContent = myVcs.hasEntry(oldFile.getPath());

    // todo try make it more clear... and refactor
    if (notAllowedOrNotUnderContentRoot(newFile)) {
      if (wasInContent) myState.delete(oldFile);
      return;
    }

    if (!wasInContent) {
      myState.create(newFile);
      return;
    }

    myState.rename(oldFile, e.getFile().getName());
  }

  @Override
  public void fileMoved(VirtualFileMoveEvent e) {
    // todo a bit messy code
    if (isMovedFromOutside(e) && isMovedToOutside(e)) return;

    if (isMovedFromOutside(e)) {
      if (notAllowedOrNotUnderContentRoot(e)) return;
      myState.create(e.getFile());
      return;
    }

    VirtualFile oldFile = new ReparentedVirtualFile(e.getOldParent(), e.getFile());

    if (isMovedToOutside(e)) {
      boolean wasInContent = myVcs.hasEntry(oldFile.getPath());
      if (wasInContent) myState.delete(oldFile);
      return;
    }

    if (notAllowedOrNotUnderContentRoot(e)) return;
    myState.move(oldFile, e.getNewParent());
  }

  @Override
  public void fileDeleted(VirtualFileEvent e) {
    VirtualFile f;
    if (e.getParent() == null) {
      f = e.getFile();
    }
    else {
      f = new ReparentedVirtualFile(e.getParent(), e.getFile());
    }

    if (!myVcs.hasEntry(f.getPath())) return;
    myState.delete(f);
  }

  private boolean notAllowedOrNotUnderContentRoot(VirtualFile f) {
    return !myFileFilter.isAllowedAndUnderContentRoot(f);
  }

  private boolean notAllowedOrNotUnderContentRoot(VirtualFileEvent e) {
    return notAllowedOrNotUnderContentRoot(e.getFile());
  }

  private boolean isMovedFromOutside(VirtualFileMoveEvent e) {
    return !myFileFilter.isUnderContentRoot(e.getOldParent());
  }

  private boolean isMovedToOutside(VirtualFileMoveEvent e) {
    return !myFileFilter.isUnderContentRoot(e.getNewParent());
  }

  public void beforeCommandFinished(CommandEvent e) {
  }

  public void undoTransparentActionStarted() {
  }

  public void undoTransparentActionFinished() {
  }

  private class ReparentedVirtualFile extends NullVirtualFile {
    private VirtualFile myParent;
    private VirtualFile myChild;

    public ReparentedVirtualFile(VirtualFile newParent, VirtualFile child) {
      myChild = child;
      myParent = newParent;
    }

    @Override
    public String getPath() {
      return Paths.appended(myParent.getPath(), myChild.getName());
    }
  }

  private class RenamedVirtualFile extends NullVirtualFile {
    private VirtualFile myFile;
    private String myNewName;

    public RenamedVirtualFile(VirtualFile f, String newName) {
      myFile = f;
      myNewName = newName;
    }

    @Override
    public String getName() {
      return myNewName;
    }

    @Override
    public String getPath() {
      return Paths.renamed(myFile.getPath(), myNewName);
    }

    @Override
    public VirtualFile getParent() {
      return myFile.getParent();
    }

    @Override
    public boolean isDirectory() {
      return myFile.isDirectory();
    }
  }

  private class NullVirtualFile extends VirtualFile {
    @NotNull
    @NonNls
    public String getName() {
      throw new UnsupportedOperationException();
    }

    @NotNull
    public VirtualFileSystem getFileSystem() {
      throw new UnsupportedOperationException();
    }

    public String getPath() {
      throw new UnsupportedOperationException();
    }

    public boolean isWritable() {
      throw new UnsupportedOperationException();
    }

    public boolean isDirectory() {
      throw new UnsupportedOperationException();
    }

    public boolean isValid() {
      throw new UnsupportedOperationException();
    }

    @Nullable
    public VirtualFile getParent() {
      throw new UnsupportedOperationException();
    }

    public VirtualFile[] getChildren() {
      throw new UnsupportedOperationException();
    }

    public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
      throw new UnsupportedOperationException();
    }

    public byte[] contentsToByteArray() throws IOException {
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
