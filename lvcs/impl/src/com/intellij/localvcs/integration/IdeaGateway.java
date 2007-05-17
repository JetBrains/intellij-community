package com.intellij.localvcs.integration;

import com.intellij.localvcs.core.ContentFactory;
import com.intellij.localvcs.core.ILocalVcs;
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
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class IdeaGateway {
  protected Project myProject;
  protected FileFilter myFileFilter;

  public IdeaGateway(Project p) {
    myProject = p;
    myFileFilter = createFileFilter();
  }

  protected FileFilter createFileFilter() {
    FileIndex fi = ProjectRootManager.getInstance(myProject).getFileIndex();
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

  public void showError(String s) {
    Messages.showErrorDialog(myProject, s, "Error");
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

  public VirtualFile findOrCreateDirectory(String path) {
    File f = new File(path);
    if (!f.exists()) f.mkdirs();

    getFileSystem().refresh(false);
    return getFileSystem().findFileByPath(path);
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

  public byte[] getPhysicalContent(VirtualFile f) throws IOException {
    return getFileSystem().physicalContentsToByteArray(f);
  }

  public long getPhysicalLength(VirtualFile f) throws IOException {
    return getFileSystem().physicalLength(f);
  }

  public void registerUnsavedDocuments(ILocalVcs vcs) {
    for (Document d : getUnsavedDocuments()) {
      VirtualFile f = getDocumentFile(d);
      if (!getFileFilter().isAllowedAndUnderContentRoot(f)) continue;
      vcs.changeFileContent(f.getPath(), contentFactoryFor(d), Clock.getCurrentTimestamp());
    }
  }

  private ContentFactory contentFactoryFor(final Document d) {
    return new ContentFactory() {
      @Override
      public byte[] getBytes() {
        return d.getText().getBytes();
      }

      @Override
      public long getLength() {
        return getBytes().length;
      }
    };
  }

  public Document getDocumentFor(VirtualFile f) {
    return getDocManager().getDocument(f);
  }

  public void saveAllUnsavedDocuments() {
    getDocManager().saveAllDocuments();
  }

  protected Document[] getUnsavedDocuments() {
    return getDocManager().getUnsavedDocuments();
  }

  protected VirtualFile getDocumentFile(Document d) {
    return getDocManager().getFile(d);
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
