package com.intellij.history.integration;

import com.intellij.CommonBundle;
import com.intellij.history.Clock;
import com.intellij.history.core.ContentFactory;
import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.Paths;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IdeaGateway {
  protected Project myProject;
  protected FileFilter myFileFilter;

  public IdeaGateway(Project p) {
    myProject = p;
    myFileFilter = createFileFilter();
  }

  protected FileFilter createFileFilter() {
    FileIndex fi = getRootManager().getFileIndex();
    FileTypeManager tm = FileTypeManager.getInstance();
    return new FileFilter(fi, tm);
  }

  public Project getProject() {
    return myProject;
  }

  // todo get rid of file filter
  public FileFilter getFileFilter() {
    return myFileFilter;
  }

  public String getBaseDir() {
    return myProject.getBaseDir().getPath();
  }

  public List<VirtualFile> getContentRoots() {
    return Arrays.asList(getRootManager().getContentRoots());
  }

  private ProjectRootManager getRootManager() {
    return ProjectRootManager.getInstance(myProject);
  }

  public boolean askForProceeding(String s) {
    return Messages.showYesNoDialog(myProject, s, CommonBundle.getWarningTitle(), Messages.getWarningIcon()) == 0;
  }

  public void showError(String s) {
    Messages.showErrorDialog(myProject, s, CommonBundle.getErrorTitle());
  }

  public void showMessage(String s, String title) {
    Messages.showInfoMessage(myProject, s, title);
  }

  public void performCommandInsideWriteAction(final String name, final Runnable r) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        performCommand(name, r);
      }
    });
  }

  private void performCommand(String name, Runnable r) {
    CommandProcessor.getInstance().executeCommand(myProject, r, name, null);
  }

  public boolean ensureFilesAreWritable(List<VirtualFile> ff) {
    ReadonlyStatusHandler h = ReadonlyStatusHandler.getInstance(myProject);
    return !h.ensureFilesWritable(ff.toArray(new VirtualFile[0])).hasReadonlyFiles();
  }

  public VirtualFile findVirtualFile(String path) {
    return getFileSystem().findFileByPath(path);
  }

  public VirtualFile findOrCreateFileSafely(String path, boolean isDirectory) throws IOException {
    return findOrCreateFileSafely(this, path, isDirectory);
  }

  public VirtualFile findOrCreateFileSafely(Object requestor, String path, boolean isDirectory) throws IOException {
    VirtualFile f = findVirtualFile(path);
    if (f != null && f.isDirectory() != isDirectory) {
      f.delete(this);
      f = null;
    }
    if (f == null) {
      VirtualFile parent = findOrCreateFileSafely(Paths.getParentOf(path), true);
      String name = Paths.getNameOf(path);
      f = isDirectory
          ? parent.createChildDirectory(requestor, name)
          : parent.createChildData(requestor, name);
    }
    return f;
  }

  public List<VirtualFile> getAllFilesFrom(String path) {
    return collectFiles(findVirtualFile(path), new ArrayList<VirtualFile>());
  }

  private List<VirtualFile> collectFiles(VirtualFile f, List<VirtualFile> result) {
    if (f.isDirectory()) {
      for (VirtualFile child : f.getChildren()) {
        collectFiles(child, result);
      }
    }
    else {
      result.add(f);
    }
    return result;
  }

  public void registerUnsavedDocuments(LocalVcs vcs) {
    vcs.beginChangeSet();
    for (Document d : getUnsavedDocuments()) {
      VirtualFile f = getFile(d);
      if (shouldNotRegister(f)) continue;
      registerDocumentContents(vcs, f, d);
    }
    vcs.endChangeSet(null);
  }

  public void registerUnsavedDocuments(LocalVcs vcs, VirtualFile f) {
    if (shouldNotRegister(f)) return;
    if (f.isDirectory()) {
      for (VirtualFile each : f.getChildren()) {
        registerUnsavedDocuments(vcs, each);
      }
    }
    else {
      registerDocumentContents(vcs, f, getDocument(f));
    }
  }

  private void registerDocumentContents(LocalVcs vcs, VirtualFile f, Document d) {
    vcs.changeFileContent(f.getPath(), contentFactoryFor(d), Clock.getCurrentTimestamp());
  }

  private boolean shouldNotRegister(VirtualFile f) {
    if (f == null) return true;
    if (!f.isValid()) return true;
    if (!getFileFilter().isAllowedAndUnderContentRoot(f)) return true;
    return false;
  }

  private ContentFactory contentFactoryFor(final Document d) {
    return new ContentFactory() {
      @Override
      public byte[] getBytes() {
        return bytesFromDocument(d);
      }

      @Override
      public long getLength() {
        return getBytes().length;
      }
    };
  }

  protected byte[] bytesFromDocument(Document d) {
    try {
      return d.getText().getBytes(getFile(d).getCharset().name());
    }
    catch (UnsupportedEncodingException e) {
      return d.getText().getBytes();
    }
  }

  public String stringFromBytes(byte[] bytes, String path) {
    try {
      VirtualFile file = findVirtualFile(path);
      if (file == null) {
        return CharsetToolkit.bytesToString(bytes);
      }
      return new String(bytes, file.getCharset().name());
    }
    catch (UnsupportedEncodingException e1) {
      return new String(bytes);
    }
  }

  public void saveAllUnsavedDocuments() {
    getDocManager().saveAllDocuments();
  }

  protected Document[] getUnsavedDocuments() {
    return getDocManager().getUnsavedDocuments();
  }

  protected VirtualFile getFile(Document d) {
    return getDocManager().getFile(d);
  }

  protected Document getDocument(VirtualFile f) {
    return getDocManager().getDocument(f);
  }

  public Document getDocument(String path) {
    return getDocument(findVirtualFile(path));
  }

  public FileType getFileType(String fileName) {
    return FileTypeManager.getInstance().getFileTypeByFileName(fileName);
  }

  private LocalFileSystem getFileSystem() {
    return LocalFileSystem.getInstance();
  }

  private FileDocumentManager getDocManager() {
    return FileDocumentManager.getInstance();
  }
}
