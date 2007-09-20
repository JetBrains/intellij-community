package com.intellij.history.integration;

import com.intellij.history.core.ILocalVcs;
import com.intellij.history.core.Paths;
import com.intellij.history.core.tree.Entry;
import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileContent;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class EventDispatcher extends VirtualFileAdapter implements VirtualFileManagerListener, CommandListener, CacheUpdater {
  private ILocalVcs myVcs;
  private IdeaGateway myGateway;
  private LocalHistoryFacade myFacade;

  private boolean isRefreshing;
  private CacheUpdaterProcessor myProcessor;

  public EventDispatcher(ILocalVcs vcs, IdeaGateway gw) {
    myVcs = vcs;
    myGateway = gw;
    myFacade = new LocalHistoryFacade(vcs, gw);
  }

  public void beforeRefreshStart(boolean asynchonous) {
    myFacade.startRefreshing();
    isRefreshing = true;
  }

  public void afterRefreshFinish(boolean asynchonous) {
    isRefreshing = false;
    myFacade.finishRefreshing();
  }

  public VirtualFile[] queryNeededFiles() {
    return getOrInitProcessor().queryNeededFiles();
  }

  public void processFile(FileContent c) {
    getOrInitProcessor().processFile(c);
  }

  public void updatingDone() {
    myProcessor = null;
  }

  public void canceled() {
    throw new UnsupportedOperationException();
  }

  public void commandStarted(CommandEvent e) {
    if (notForMe(e)) return;
    myFacade.startCommand();
  }

  public void commandFinished(CommandEvent e) {
    if (notForMe(e)) return;
    myFacade.finishCommand(e.getCommandName());
  }

  private boolean notForMe(CommandEvent e) {
    return e.getProject() != myGateway.getProject();
  }

  public void startAction() {
    myFacade.startAction();
  }

  public void finishAction(String name) {
    myFacade.finishAction(name);
  }

  @Override
  public void fileCreated(VirtualFileEvent e) {
    if (notAllowedOrNotUnderContentRoot(e)) return;
    VirtualFile f = e.getFile();

    if (e.getRequestor() instanceof Entry) {
      myFacade.restore(f, (Entry)e.getRequestor());
    }
    else {
      if (wasCreatedDuringRootsUpdate(f)) return;
      create(f);
    }
  }

  private boolean wasCreatedDuringRootsUpdate(VirtualFile f) {
    return myVcs.hasEntry(f.getPath());
  }

  @Override
  public void contentsChanged(VirtualFileEvent e) {
    if (notAllowedOrNotUnderContentRoot(e)) return;
    assert e.getFile().isValid();
    changeContent(e.getFile());
  }

  private void create(VirtualFile fileOrDir) {
    if (!isRefreshing) {
      myFacade.create(fileOrDir);
      return;
    }

    List<VirtualFile> files = myFacade.createOnlyDirectories(fileOrDir);
    for (VirtualFile f : files) {
      getOrInitProcessor().addFileToCreate(f);
    }
  }

  private void changeContent(VirtualFile f) {
    if (isRefreshing) {
      getOrInitProcessor().addFileToUpdate(f);
    }
    else {
      myFacade.changeFileContent(f);
    }
  }

  private CacheUpdaterProcessor getOrInitProcessor() {
    if (myProcessor == null) myProcessor = new CacheUpdaterProcessor(myVcs);
    return myProcessor;
  }

  @Override
  public void propertyChanged(VirtualFilePropertyEvent e) {
    if (e.getPropertyName().equals(VirtualFile.PROP_NAME)) {
      fileRenamed(e);
    }

    if (e.getPropertyName().equals(VirtualFile.PROP_WRITABLE)) {
      readOnlyStatusChanged(e);
    }
  }

  private void fileRenamed(VirtualFilePropertyEvent e) {
    VirtualFile newFile = e.getFile();
    VirtualFile oldFile = new RenamedVirtualFile(e.getFile(), (String)e.getOldValue());
    boolean wasInContent = myVcs.hasEntry(oldFile.getPath());

    // todo try make it more clear... and refactor
    if (notAllowedOrNotUnderContentRoot(newFile)) {
      if (wasInContent) myFacade.delete(oldFile);
      return;
    }

    if (!wasInContent) {
      myFacade.create(newFile);
      return;
    }

    myFacade.rename(oldFile, e.getFile().getName());
  }

  private void readOnlyStatusChanged(VirtualFilePropertyEvent e) {
    if (notAllowedOrNotUnderContentRoot(e)) return;
    
    VirtualFile f = e.getFile();
    if (f.isDirectory()) return;
    myFacade.changeROStatus(f);
  }

  @Override
  public void fileMoved(VirtualFileMoveEvent e) {
    // todo a bit messy code
    if (isMovedFromOutside(e) && isMovedToOutside(e)) return;

    if (isMovedFromOutside(e)) {
      if (notAllowedOrNotUnderContentRoot(e)) return;
      myFacade.create(e.getFile());
      return;
    }

    VirtualFile oldFile = new ReparentedVirtualFile(e.getOldParent(), e.getFile());

    if (isMovedToOutside(e)) {
      boolean wasInContent = myVcs.hasEntry(oldFile.getPath());
      if (wasInContent) myFacade.delete(oldFile);
      return;
    }

    if (notAllowedOrNotUnderContentRoot(e)) return;
    myFacade.move(oldFile, e.getNewParent());
  }

  @Override
  public void fileDeleted(VirtualFileEvent e) {
    VirtualFile f = e.getFile();
    if (wasDeletedDuringRootsUpdate(f)) return;
    myFacade.delete(f);
  }

  private boolean wasDeletedDuringRootsUpdate(VirtualFile f) {
    return !myVcs.hasEntry(f.getPath());
  }

  private boolean notAllowedOrNotUnderContentRoot(VirtualFile f) {
    return !getFileFilter().isAllowedAndUnderContentRoot(f);
  }

  private boolean notAllowedOrNotUnderContentRoot(VirtualFileEvent e) {
    return notAllowedOrNotUnderContentRoot(e.getFile());
  }

  private boolean isMovedFromOutside(VirtualFileMoveEvent e) {
    return !getFileFilter().isUnderContentRoot(e.getOldParent());
  }

  private boolean isMovedToOutside(VirtualFileMoveEvent e) {
    return !getFileFilter().isUnderContentRoot(e.getNewParent());
  }

  public void beforeCommandFinished(CommandEvent e) {
  }

  public void undoTransparentActionStarted() {
  }

  public void undoTransparentActionFinished() {
  }

  private FileFilter getFileFilter() {
    return myGateway.getFileFilter();
  }

  private static class ReparentedVirtualFile extends NullVirtualFile {
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

  private static class RenamedVirtualFile extends NullVirtualFile {
    private VirtualFile myFile;
    private String myNewName;

    public RenamedVirtualFile(VirtualFile f, String newName) {
      myFile = f;
      myNewName = newName;
    }

    @NotNull
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

  private static class NullVirtualFile extends DeprecatedVirtualFile {
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
