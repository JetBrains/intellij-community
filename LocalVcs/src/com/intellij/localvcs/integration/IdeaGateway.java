package com.intellij.localvcs.integration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.concurrent.Callable;

public class IdeaGateway {
  private Project myProject;

  public IdeaGateway(Project p) {
    myProject = p;
  }

  public Project getProject() {
    return myProject;
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

  public byte[] getDocumentByteContent(VirtualFile f) {
    // todo review byte conversion
    FileDocumentManager dm = FileDocumentManager.getInstance();
    return dm.getDocument(f).getText().getBytes();
  }
}
