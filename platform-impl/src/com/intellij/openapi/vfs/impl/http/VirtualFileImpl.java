package com.intellij.openapi.vfs.impl.http;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class VirtualFileImpl extends HttpVirtualFile {
  private final HttpFileSystemImpl myFileSystem;
  private final RemoteFileInfo myFileInfo;
  private String myPath;
  private String myParentPath;
  private String myName;

  VirtualFileImpl(HttpFileSystemImpl fileSystem, String path, final RemoteFileInfo fileInfo) {
    myFileSystem = fileSystem;
    myPath = path;
    myFileInfo = fileInfo;
    myFileInfo.addDownloadingListener(new FileDownloadingAdapter() {
      public void fileDownloaded(final VirtualFile localFile) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            FileDocumentManager.getInstance().reloadFiles(VirtualFileImpl.this);
          }
        });
      }
    });
    int lastSlash = path.lastIndexOf('/');
    if (lastSlash == path.length() - 1) {
      myParentPath = null;
      myName = path;
    }
    else {
      int prevSlash = path.lastIndexOf('/', lastSlash - 1);
      if (prevSlash < 0) {
        myParentPath = path.substring(0, lastSlash + 1);
        myName = path.substring(lastSlash + 1);
      }
      else {
        myParentPath = path.substring(0, lastSlash);
        myName = path.substring(lastSlash + 1);
      }
    }
  }

  public RemoteFileInfo getFileInfo() {
    return myFileInfo;
  }

  @NotNull
  public VirtualFileSystem getFileSystem() {
    return myFileSystem;
  }

  public String getPath() {
    return myPath;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public VirtualFile getParent() {
    if (myParentPath == null) return null;
    return myFileSystem.findFileByPath(myParentPath);
  }

  public boolean isWritable() {
    return false;
  }

  public boolean isValid() {
    return true;
  }

  public boolean isDirectory() {
    return false;
  }

  public VirtualFile[] getChildren() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public FileType getFileType() {
    VirtualFile localFile = myFileInfo.getLocalFile();
    if (localFile != null) {
      return localFile.getFileType();
    }
    return super.getFileType();
  }

  public InputStream getInputStream() throws IOException {
    VirtualFile localFile = myFileInfo.getLocalFile();
    if (localFile != null) {
      return localFile.getInputStream();
    }
    throw new UnsupportedOperationException();
  }

  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    VirtualFile localFile = myFileInfo.getLocalFile();
    if (localFile != null) {
      return localFile.getOutputStream(requestor, newModificationStamp, newTimeStamp);
    }
    throw new UnsupportedOperationException();
  }

  public byte[] contentsToByteArray() throws IOException {
    VirtualFile localFile = myFileInfo.getLocalFile();
    if (localFile != null) {
      return localFile.contentsToByteArray();
    }
    return ArrayUtil.EMPTY_BYTE_ARRAY;
  }

  public long getTimeStamp() {
    return 0;
  }

  public long getModificationStamp() {
    return 0;
  }

  public long getLength() {
    return -1;
  }

  public void refresh(final boolean asynchronous, final boolean recursive, final Runnable postRunnable) {
    if (postRunnable != null) {
      postRunnable.run();
    }
  }
}