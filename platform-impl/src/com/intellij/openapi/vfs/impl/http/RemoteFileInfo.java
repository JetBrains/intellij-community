package com.intellij.openapi.vfs.impl.http;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author nik
 */
public class RemoteFileInfo implements RemoteContentProvider.DownloadingCallback {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.http.RemoteFileInfo");
  private final Object myLock = new Object();
  private final String myUrl;
  private final RemoteFileManager myManager;
  private final RemoteContentProvider myContentProvider;
  private File myLocalFile;
  private VirtualFile myLocalVirtualFile;
  private boolean myDownloaded;
  private String myErrorMessage;
  private volatile boolean myCancelled;
  private List<FileDownloadingListener> myListeners = new SmartList<FileDownloadingListener>();

  public RemoteFileInfo(final @NotNull String url, final @NotNull RemoteFileManager manager, final @NotNull RemoteContentProvider provider) {
    myUrl = url;
    myManager = manager;
    myContentProvider = provider;
  }

  public void addDownloadingListener(@NotNull FileDownloadingListener listener) {
    synchronized (myLock) {
      myListeners.add(listener);
    }
  }

  public void removeDownloadingListener(final @NotNull FileDownloadingListener listener) {
    synchronized (myLock) {
      myListeners.remove(listener);
    }
  }

  public String getUrl() {
    return myUrl;
  }

  public void restartDownloading() {
    synchronized (myLock) {
      myErrorMessage = null;
      myLocalVirtualFile = null;
      myDownloaded = false;
      myLocalFile = null;
      startDownloading();
    }
  }

  public void startDownloading() {
    LOG.debug("Downloading requested");

    File localFile;
    synchronized (myLock) {
      if (myDownloaded) {
        LOG.debug("File already downloaded: " + myLocalVirtualFile);
        return;
      }
      if (myErrorMessage != null) {
        LOG.debug("Error occured: " + myErrorMessage);
        return;
      }

      if (myLocalFile != null) {
        LOG.debug("Downloading in progress");
        return;
      }

      try {
        myLocalFile = myManager.getStorage().createLocalFile(myUrl);
        LOG.debug("Local file created: " + myLocalFile.getAbsolutePath());
      }
      catch (IOException e) {
        LOG.info(e);
        errorOccured(VfsBundle.message("cannot.create.local.file", e.getMessage()));
        return;
      }
      myCancelled = false;
      localFile = myLocalFile;
    }

    myContentProvider.saveContent(myUrl, localFile, this);
  }

  public void finished(@Nullable final FileType fileType) {
    final File localIOFile;

    synchronized (myLock) {
      LOG.debug("Downloading finished, size = " + myLocalFile.length() + ", file type=" + fileType);
      if (fileType != null) {
        String fileName = myLocalFile.getName();
        int dot = fileName.lastIndexOf('.');
        String extension = fileType.getDefaultExtension();
        if (dot == -1 || !extension.equals(fileName.substring(dot + 1))) {
          File newFile = FileUtil.findSequentNonexistentFile(myLocalFile.getParentFile(), fileName, extension);
          try {
            FileUtil.rename(myLocalFile, newFile);
            myLocalFile = newFile;
          }
          catch (IOException e) {
            LOG.debug(e);
          }
        }
      }

      localIOFile = myLocalFile;
    }

    VirtualFile localFile = new WriteAction<VirtualFile>() {
      protected void run(final Result<VirtualFile> result) {
        result.setResult(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(localIOFile));
      }
    }.execute().getResultObject();
    LOG.debug("Virtual local file: " + localFile + ", size = " + localFile.getLength());
    FileDownloadingListener[] listeners;
    synchronized (myLock) {
      myLocalVirtualFile = localFile;
      myDownloaded = true;
      myErrorMessage = null;
      listeners = myListeners.toArray(new FileDownloadingListener[myListeners.size()]);
    }
    for (FileDownloadingListener listener : listeners) {
      listener.fileDownloaded(localFile);
    }
  }

  public boolean isCancelled() {
    return myCancelled;
  }

  public String getErrorMessage() {
    synchronized (myLock) {
      return myErrorMessage;
    }
  }

  public void errorOccured(@NotNull final String errorMessage) {
    LOG.debug("Error: " + errorMessage);
    FileDownloadingListener[] listeners;
    synchronized (myLock) {
      myLocalVirtualFile = null;
      myErrorMessage = errorMessage;
      listeners = myListeners.toArray(new FileDownloadingListener[myListeners.size()]);
    }
    for (FileDownloadingListener listener : listeners) {
      listener.errorOccured(errorMessage);
    }
  }

  public void setProgressFraction(final double fraction) {
    FileDownloadingListener[] listeners;
    synchronized (myLock) {
      listeners = myListeners.toArray(new FileDownloadingListener[myListeners.size()]);
    }
    for (FileDownloadingListener listener : listeners) {
      listener.progressFractionChanged(fraction);
    }
  }

  public void setProgressText(@NotNull final String text, final boolean indeterminate) {
    FileDownloadingListener[] listeners;
    synchronized (myLock) {
      listeners = myListeners.toArray(new FileDownloadingListener[myListeners.size()]);
    }
    for (FileDownloadingListener listener : listeners) {
      listener.progressMessageChanged(indeterminate, text);
    }
  }

  public VirtualFile getLocalFile() {
    synchronized (myLock) {
      return myLocalVirtualFile;
    }
  }

  public boolean isDownloaded() {
    synchronized (myLock) {
      return myDownloaded;
    }
  }

  public void cancelDownloading() {
    synchronized (myLock) {
      myCancelled = true;
    }
  }

  public void refresh(final @Nullable Runnable postRunnable) {
    VirtualFile localVirtualFile;
    synchronized (myLock) {
      localVirtualFile = myLocalVirtualFile;
    }
    if ((localVirtualFile == null || !myContentProvider.isUpToDate(myUrl, localVirtualFile)) && myContentProvider.canProvideContent(myUrl)) {
      addDownloadingListener(new MyRefreshingDownloadingListener(postRunnable));
      restartDownloading();
    }
  }

  private class MyRefreshingDownloadingListener extends FileDownloadingAdapter {
    private final Runnable myPostRunnable;

    public MyRefreshingDownloadingListener(final Runnable postRunnable) {
      myPostRunnable = postRunnable;
    }

    @Override
    public void fileDownloaded(final VirtualFile localFile) {
      removeDownloadingListener(this);
      if (myPostRunnable != null) {
        myPostRunnable.run();
      }
    }

    @Override
    public void errorOccured(@NotNull final String errorMessage) {
      removeDownloadingListener(this);
      if (myPostRunnable != null) {
        myPostRunnable.run();
      }
    }
  }

}
