package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mike
 */
public class FileStatusManagerImpl extends FileStatusManager implements ProjectComponent {
  private final HashMap<VirtualFile, FileStatus> myCachedStatuses = new HashMap<VirtualFile, FileStatus>();

  private Project myProject;
  private List<FileStatusListener> myListeners = new ArrayList<FileStatusListener>();
  private MyDocumentAdapter myDocumentListener;
  private FileStatusProvider myFileStatusProvider;

  public FileStatusManagerImpl(Project project,
                               StartupManager startupManager) {
    myProject = project;

    startupManager.registerPostStartupActivity(new Runnable() {
      public void run() {
        fileStatusesChanged();
      }
    });
  }

  public void setFileStatusProvider(final FileStatusProvider fileStatusProvider) {
    myFileStatusProvider = fileStatusProvider;
  }

  public FileStatus calcStatus(@NotNull VirtualFile virtualFile) {
    if (virtualFile.isInLocalFileSystem() && myFileStatusProvider != null) {
      return myFileStatusProvider.getFileStatus(virtualFile);
    } else {
      return FileStatus.NOT_CHANGED;
    }
  }

  public void projectClosed() {
    EditorFactory.getInstance().getEventMulticaster().removeDocumentListener(myDocumentListener);
  }

  public void projectOpened() {
    myDocumentListener = new MyDocumentAdapter();
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(myDocumentListener);
  }

  public void disposeComponent() {
    myCachedStatuses.clear();
  }

  @NotNull
  public String getComponentName() {
    return "FileStatusManager";
  }

  public void initComponent() { }

  public void addFileStatusListener(FileStatusListener listener) {
    myListeners.add(listener);
  }

  public void addFileStatusListener(final FileStatusListener listener, Disposable parentDisposable) {
    addFileStatusListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        removeFileStatusListener(listener);
      }
    });
  }

  public void fileStatusesChanged() {
    if (myProject.isDisposed()) {
      return;
    }
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          fileStatusesChanged();
        }
      }, ModalityState.NON_MODAL);
      return;
    }

    myCachedStatuses.clear();

    final FileStatusListener[] listeners = myListeners.toArray(new FileStatusListener[myListeners.size()]);
    for (FileStatusListener listener : listeners) {
      listener.fileStatusesChanged();
    }
  }

  public void fileStatusChanged(final VirtualFile file) {
    final Application application = ApplicationManager.getApplication();
    if (!application.isDispatchThread() && !application.isUnitTestMode()) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          fileStatusChanged(file);
        }
      });
      return;
    }

    if (!file.isValid()) return;
    FileStatus cachedStatus = getCachedStatus(file);
    if (cachedStatus == null) return;
    FileStatus newStatus = calcStatus(file);
    if (cachedStatus == newStatus) return;
    myCachedStatuses.put(file, newStatus);

    final FileStatusListener[] listeners = myListeners.toArray(new FileStatusListener[myListeners.size()]);
    for (FileStatusListener listener : listeners) {
      listener.fileStatusChanged(file);
    }
  }

  public FileStatus getStatus(final VirtualFile file) {
    FileStatus status = getCachedStatus(file);
    if (status == null) {
      status = calcStatus(file);
      myCachedStatuses.put(file, status);
    }

    return status;
  }

  public FileStatus getCachedStatus(final VirtualFile file) {
    return myCachedStatuses.get(file);
  }

  public void removeFileStatusListener(FileStatusListener listener) {
    myListeners.remove(listener);
  }

  public void refreshFileStatusFromDocument(final VirtualFile file, final Document doc) {
    if (myFileStatusProvider != null) {
      myFileStatusProvider.refreshFileStatusFromDocument(file, doc);
    }
  }

  private class MyDocumentAdapter extends DocumentAdapter {
    public void documentChanged(DocumentEvent event) {
      VirtualFile file = FileDocumentManager.getInstance().getFile(event.getDocument());
      if (file != null) {
        refreshFileStatusFromDocument(file, event.getDocument());
      }
    }
  }
}
