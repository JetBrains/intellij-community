package com.intellij.openapi.vfs.impl.http;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import gnu.trove.THashMap;

import java.io.IOException;
import java.util.Map;

/**
 * @author nik
 */
public class RemoteFileManager {
  private LocalFileStorage myStorage;
  private final HttpFileSystemImpl myHttpFileSystem;
  private Map<Pair<Boolean, String>, VirtualFileImpl> myRemoteFiles;

  public RemoteFileManager(final HttpFileSystemImpl httpFileSystem) {
    myHttpFileSystem = httpFileSystem;
    myStorage = new LocalFileStorage();
    myRemoteFiles = new THashMap<Pair<Boolean, String>, VirtualFileImpl>();
  }


  public synchronized VirtualFileImpl getOrCreateFile(final String path, final boolean directory) throws IOException {
    String url = VirtualFileManager.constructUrl(HttpFileSystem.PROTOCOL, path);
    Pair<Boolean, String> key = Pair.create(directory, url);
    VirtualFileImpl file = myRemoteFiles.get(key);
    if (file == null) {
      if (!directory) {
        RemoteFileInfo fileInfo = new RemoteFileInfo(url, this);
        file = new VirtualFileImpl(myHttpFileSystem, path, fileInfo);
        fileInfo.addDownloadingListener(new MyDownloadingListener(myHttpFileSystem, file));
      }
      else {
        file = new VirtualFileImpl(myHttpFileSystem, path, null);
      }
      myRemoteFiles.put(key, file);
    }
    return file;
  }

  public LocalFileStorage getStorage() {
    return myStorage;
  }


  private static class MyDownloadingListener extends FileDownloadingAdapter {
    private final HttpFileSystemImpl myHttpFileSystem;
    private final VirtualFileImpl myFile;

    public MyDownloadingListener(final HttpFileSystemImpl httpFileSystem, final VirtualFileImpl file) {
      myHttpFileSystem = httpFileSystem;
      myFile = file;
    }

    public void fileDownloaded(final VirtualFile localFile) {
      myHttpFileSystem.fireFileDownloaded(myFile);
    }
  }
}
