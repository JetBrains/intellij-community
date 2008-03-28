package com.intellij.openapi.vfs.impl.http;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SmartList;
import com.intellij.util.io.UrlConnectionUtil;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * @author nik
 */
public class RemoteFileInfo {
  private static final int CONNECT_TIMEOUT = 60 * 1000;
  private static final int READ_TIMEOUT = 60 * 1000;
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.http.RemoteFileInfo");
  private final Object myLock = new Object();
  private final String myUrl;
  private final RemoteFileManager myManager;
  private File myLocalFile;
  private VirtualFile myLocalVirtualFile;
  private boolean myDownloaded;
  private String myErrorMessage;
  private volatile boolean myCancelled;
  private List<FileDownloadingListener> myListeners = new SmartList<FileDownloadingListener>();

  public RemoteFileInfo(final String url, final RemoteFileManager manager) {
    myUrl = url;
    myManager = manager;
  }

  public void addDownloadingListener(@NotNull FileDownloadingListener listener) {
    synchronized (myLock) {
      myListeners.add(listener);
    }
  }

  public String getUrl() {
    return myUrl;
  }

  public void restartDownloading(@NotNull FileDownloadingListener listener) {
    synchronized (myLock) {
      myErrorMessage = null;
      myLocalVirtualFile = null;
      myDownloaded = false;
      myLocalFile = null;
      startDownloading(listener);
    }
  }

  public void startDownloading(@NotNull FileDownloadingListener listener) {
    LOG.debug("Downloading requested");

    synchronized (myLock) {
      if (myDownloaded) {
        LOG.debug("File already downloaded: " + myLocalVirtualFile);
        listener.fileDownloaded(myLocalVirtualFile);
        return;
      }
      if (myErrorMessage != null) {
        LOG.debug("Error occured: " + myErrorMessage);
        listener.errorOccured(myErrorMessage);
        return;
      }

      myListeners.add(listener);
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
        updateState(VfsBundle.message("cannot.create.local.file", e.getMessage()));
        return;
      }
      myCancelled = false;
    }

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        HttpConfigurable.getInstance().setAuthenticator();
        LOG.debug("Downloading started");
        InputStream input = null;
        OutputStream output = null;
        try {
          updateProgress(true, VfsBundle.message("download.progress.connecting", myUrl));
          HttpURLConnection connection = (HttpURLConnection)new URL(myUrl).openConnection();
          connection.setConnectTimeout(CONNECT_TIMEOUT);
          connection.setReadTimeout(READ_TIMEOUT);
          input = UrlConnectionUtil.getConnectionInputStreamWithException(connection, new EmptyProgressIndicator());

          final int responseCode = connection.getResponseCode();
          if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException(IdeBundle.message("error.connection.failed.with.http.code.N", responseCode));
          }

          final int size = connection.getContentLength();
          output = new BufferedOutputStream(new FileOutputStream(myLocalFile));
          updateProgress(size == -1, VfsBundle.message("download.progress.downloading", myUrl));
          if (size != -1) {
            setProgressFraction(0);
          }
          String contentType = connection.getContentType();
          FileType fileType = RemoteFileUtil.getFileType(contentType);

          int len;
          final byte[] buf = new byte[1024];
          int count = 0;
          while ((len = input.read(buf)) > 0) {
            if (myCancelled) {
              updateState(VfsBundle.message("downloading.cancelled.message"));
              return;
            }
            count += len;
            if (size > 0) {
              setProgressFraction((double)count / size);
            }
            output.write(buf, 0, len);
          }
          output.close();
          output = null;
          LOG.debug("Downloading finished, " + size + " bytes downloaded");
          updateState(fileType);
        }
        catch (IOException e) {
          LOG.info(e);
          updateState(VfsBundle.message("cannot.load.remote.file", myUrl, e.getMessage()));
        }
        finally {
          if (input != null) {
            try {
              input.close();
            }
            catch (IOException e) {
              LOG.info(e);
            }
          }
          if (output != null) {
            try {
              output.close();
            }
            catch (IOException e) {
              LOG.info(e);
            }
          }
        }
      }
    });
  }

  private void updateState(final @Nullable FileType fileType) {
    synchronized (myLock) {
      if (fileType != null) {
        File newFile = new File(myLocalFile.getAbsolutePath() + "." + fileType.getDefaultExtension());
        try {
          FileUtil.rename(myLocalFile, newFile);
          myLocalFile = newFile;
        }
        catch (IOException e) {
          LOG.debug(e);
        }
      }
    }

    VirtualFile localFile = new WriteAction<VirtualFile>() {
      protected void run(final Result<VirtualFile> result) {
        result.setResult(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(myLocalFile));
      }
    }.execute().getResultObject();
    LOG.debug("Virtual local file: " + localFile);
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

  private void updateState(final String errorMessage) {
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

  private void setProgressFraction(final double fraction) {
    FileDownloadingListener[] listeners;
    synchronized (myLock) {
      listeners = myListeners.toArray(new FileDownloadingListener[myListeners.size()]);
    }
    for (FileDownloadingListener listener : listeners) {
      listener.progressFractionChanged(fraction);
    }
  }

  private void updateProgress(boolean indeterminate, String progressText) {
    FileDownloadingListener[] listeners;
    synchronized (myLock) {
      listeners = myListeners.toArray(new FileDownloadingListener[myListeners.size()]);
    }
    for (FileDownloadingListener listener : listeners) {
      listener.progressMessageChanged(indeterminate, progressText);
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

}
