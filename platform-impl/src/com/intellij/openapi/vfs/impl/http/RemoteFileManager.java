package com.intellij.openapi.vfs.impl.http;

import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFile;
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
  private Map<String, VirtualFileImpl> myRemoteFiles;

  public RemoteFileManager(final HttpFileSystemImpl httpFileSystem) {
    myHttpFileSystem = httpFileSystem;
    myStorage = new LocalFileStorage();
    myRemoteFiles = new THashMap<String, VirtualFileImpl>();
  }


  public synchronized VirtualFileImpl getOrCreateFile(final String path) throws IOException {
    String url = VirtualFileManager.constructUrl(HttpFileSystem.PROTOCOL, path);
    VirtualFileImpl file = myRemoteFiles.get(url);
    if (file == null) {
      RemoteFileInfo fileInfo = new RemoteFileInfo(url, this);
      file = new VirtualFileImpl(myHttpFileSystem, path, fileInfo);
      fileInfo.addDownloadingListener(new MyDownloadingListener(myHttpFileSystem, file));
      myRemoteFiles.put(url, file);
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
