package com.intellij.localvcs.integration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.concurrent.Callable;

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

  public FileFilter getFileFilter() {
    return myFileFilter;
  }

  public <T> T runWriteAction(final Callable<T> c) {
    return ApplicationManager.getApplication().runWriteAction(new Computable<T>() {
      public T compute() {
        try {
          return c.call();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  public boolean ensureFilesAreWritable(VirtualFile... ff) {
    ReadonlyStatusHandler h = ReadonlyStatusHandler.getInstance(myProject);
    return !h.ensureFilesWritable(ff).hasReadonlyFiles();
  }

  public byte[] getPhysicalContent(VirtualFile f) throws IOException {
    return LocalFileSystem.getInstance().physicalContentsToByteArray(f);
  }

  public byte[] getDocumentByteContent(VirtualFile f) {
    // todo review charset conversion
    FileDocumentManager dm = FileDocumentManager.getInstance();
    return dm.getDocument(f).getText().getBytes();
  }

  public Document[] getUnsavedDocuments() {
    return FileDocumentManager.getInstance().getUnsavedDocuments();
  }

  public VirtualFile getDocumentFile(Document d) {
    return FileDocumentManager.getInstance().getFile(d);
  }
}
