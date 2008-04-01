package com.intellij.openapi.vfs.impl.http;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;

class VirtualFileImpl extends HttpVirtualFile {
  private final HttpFileSystemImpl myFileSystem;
  private final @Nullable RemoteFileInfo myFileInfo;
  private FileType myInitialFileType;
  private String myPath;
  private String myParentPath;
  private String myName;

  VirtualFileImpl(HttpFileSystemImpl fileSystem, String path, final @Nullable RemoteFileInfo fileInfo) {
    myFileSystem = fileSystem;
    myPath = path;
    myFileInfo = fileInfo;
    if (myFileInfo != null) {
      myFileInfo.addDownloadingListener(new FileDownloadingAdapter() {
        public void fileDownloaded(final VirtualFile localFile) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              VirtualFileImpl file = VirtualFileImpl.this;
              FileDocumentManager.getInstance().reloadFiles(file);
              if (!localFile.getFileType().equals(myInitialFileType)) {

                VFilePropertyChangeEvent event = new VFilePropertyChangeEvent(this, file, PROP_NAME, file.getName(), file.getName(), false);
                BulkFileListener publisher = ApplicationManager.getApplication().getMessageBus().asyncPublisher(VirtualFileManager.VFS_CHANGES);
                publisher.after(Collections.singletonList(event));
              }
            }
          });
        }
      });
    }
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

  @Nullable
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
    return myFileSystem.findFileByPath(myParentPath, true);
  }

  public boolean isWritable() {
    return false;
  }

  public boolean isValid() {
    return true;
  }

  public boolean isDirectory() {
    return myFileInfo == null;
  }

  public VirtualFile[] getChildren() {
    if (myFileInfo == null) {
      return EMPTY_ARRAY;
    }
    throw new UnsupportedOperationException();
  }

  @NotNull
  public FileType getFileType() {
    if (myFileInfo == null) {
      return super.getFileType();
    }

    VirtualFile localFile = myFileInfo.getLocalFile();
    if (localFile != null) {
      return localFile.getFileType();
    }
    FileType fileType = super.getFileType();
    if (myInitialFileType == null) {
      myInitialFileType = fileType;
    }
    return fileType;
  }

  public InputStream getInputStream() throws IOException {
    if (myFileInfo != null) {
      VirtualFile localFile = myFileInfo.getLocalFile();
      if (localFile != null) {
        return localFile.getInputStream();
      }
    }
    throw new UnsupportedOperationException();
  }

  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    if (myFileInfo != null) {
      VirtualFile localFile = myFileInfo.getLocalFile();
      if (localFile != null) {
        return localFile.getOutputStream(requestor, newModificationStamp, newTimeStamp);
      }
    }
    throw new UnsupportedOperationException();
  }

  public byte[] contentsToByteArray() throws IOException {
    if (myFileInfo == null) {
      throw new UnsupportedOperationException();
    }

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