package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ksafonov
 */
public abstract class JarDirectoryWatcher implements Disposable {

  private final JarDirectories myJarDirectories;
  private MessageBusConnection myBusConnection = null;
  private final List<LocalFileSystem.WatchRequest> myWatchRequests = new ArrayList<LocalFileSystem.WatchRequest>();

  public JarDirectoryWatcher(JarDirectories jarDirectories) {
    myJarDirectories = jarDirectories;
  }

  public void updateWatchedRoots() {
    final LocalFileSystem fs = LocalFileSystem.getInstance();
    if (!myWatchRequests.isEmpty()) {
      fs.removeWatchedRoots(myWatchRequests);
      myWatchRequests.clear();
    }
    if (!myJarDirectories.isEmpty()) {
      final VirtualFileManager fm = VirtualFileManager.getInstance();
      for (OrderRootType rootType : myJarDirectories.getRootTypes()) {
        for (String url : myJarDirectories.getDirectories(rootType)) {
          if (fm.getFileSystem(VirtualFileManager.extractProtocol(url)) instanceof LocalFileSystem) {
            final boolean watchRecursively = myJarDirectories.isRecursive(rootType, url);
            final LocalFileSystem.WatchRequest request = fs.addRootToWatch(VirtualFileManager.extractPath(url), watchRecursively);
            myWatchRequests.add(request);
          }
        }
      }
      if (myBusConnection == null) {
        myBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
        myBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
          @Override
          public void before(@NotNull final List<? extends VFileEvent> events) {
          }

          @Override
          public void after(@NotNull final List<? extends VFileEvent> events) {
            boolean changesDetected = false;
            for (VFileEvent event : events) {
              if (event instanceof VFileCopyEvent) {
                final VFileCopyEvent copyEvent = (VFileCopyEvent)event;
                if (isUnderJarDirectory(copyEvent.getNewParent() + "/" + copyEvent.getNewChildName()) ||
                    isUnderJarDirectory(copyEvent.getFile().getUrl())) {
                  changesDetected = true;
                  break;
                }
              }
              else if (event instanceof VFileMoveEvent) {
                final VFileMoveEvent moveEvent = (VFileMoveEvent)event;

                final VirtualFile file = moveEvent.getFile();
                if (isUnderJarDirectory(file.getUrl()) || isUnderJarDirectory(moveEvent.getOldParent().getUrl() + "/" + file.getName())) {
                  changesDetected = true;
                  break;
                }
              }
              else if (event instanceof VFileDeleteEvent) {
                final VFileDeleteEvent deleteEvent = (VFileDeleteEvent)event;
                if (isUnderJarDirectory(deleteEvent.getFile().getUrl())) {
                  changesDetected = true;
                  break;
                }
              }
              else if (event instanceof VFileCreateEvent) {
                final VFileCreateEvent createEvent = (VFileCreateEvent)event;
                if (isUnderJarDirectory(createEvent.getParent().getUrl() + "/" + createEvent.getChildName())) {
                  changesDetected = true;
                  break;
                }
              }
            }

            if (changesDetected) {
              fireRootSetChanged();
            }
          }

          private boolean isUnderJarDirectory(String url) {
            for (String rootUrl : myJarDirectories.getAllDirectories()) {
              if (FileUtil.startsWith(url, rootUrl)) {
                return true;
              }
            }
            return false;
          }
        });
      }
    }
    else {
      final MessageBusConnection connection = myBusConnection;
      if (connection != null) {
        myBusConnection = null;
        connection.disconnect();
      }
    }
  }

  protected abstract void fireRootSetChanged();

  @Override
  public void dispose() {
    if (!myWatchRequests.isEmpty()) {
      LocalFileSystem.getInstance().removeWatchedRoots(myWatchRequests);
      myWatchRequests.clear();
    }
    if (myBusConnection != null) {
      myBusConnection.disconnect();
      myBusConnection = null;
    }
  }
}
