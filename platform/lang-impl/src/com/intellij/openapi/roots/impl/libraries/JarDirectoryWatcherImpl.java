/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.RootProviderBaseImpl;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author ksafonov
 */
public class JarDirectoryWatcherImpl implements JarDirectoryWatcher {
  private final JarDirectories myJarDirectories;
  private final RootProviderBaseImpl myRootProvider;
  private MessageBusConnection myBusConnection = null;
  private Collection<LocalFileSystem.WatchRequest> myWatchRequests = Collections.emptySet();

  public JarDirectoryWatcherImpl(JarDirectories jarDirectories, RootProviderBaseImpl rootProvider) {
    myJarDirectories = jarDirectories;
    myRootProvider = rootProvider;
  }

  @Override
  public void updateWatchedRoots() {
    final LocalFileSystem fs = LocalFileSystem.getInstance();
    if (!myJarDirectories.isEmpty()) {
      final Set<String> recursiveRoots = new HashSet<>();
      final Set<String> flatRoots = new HashSet<>();
      final VirtualFileManager fm = VirtualFileManager.getInstance();
      for (OrderRootType rootType : myJarDirectories.getRootTypes()) {
        for (String url : myJarDirectories.getDirectories(rootType)) {
          if (fm.getFileSystem(VirtualFileManager.extractProtocol(url)) instanceof LocalFileSystem) {
            final boolean watchRecursively = myJarDirectories.isRecursive(rootType, url);
            final String path = VirtualFileManager.extractPath(url);
            (watchRecursively ? recursiveRoots : flatRoots).add(path);
          }
        }
      }

      myWatchRequests = fs.replaceWatchedRoots(myWatchRequests, recursiveRoots, flatRoots);

      if (myBusConnection == null) {
        myBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
        myBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener.Adapter() {
          @Override
          public void after(@NotNull final List<? extends VFileEvent> events) {
            boolean changesDetected = false;
            for (VFileEvent event : events) {
              if (event instanceof VFileCopyEvent) {
                final VFileCopyEvent copyEvent = (VFileCopyEvent)event;
                final VirtualFile file = copyEvent.getFile();
                if (isUnderJarDirectory(copyEvent.getNewParent() + "/" + copyEvent.getNewChildName()) ||
                    file != null && isUnderJarDirectory(file.getUrl())) {
                  changesDetected = true;
                  break;
                }
              }
              else if (event instanceof VFileMoveEvent) {
                final VFileMoveEvent moveEvent = (VFileMoveEvent)event;
                final VirtualFile file = moveEvent.getFile();
                if (file != null &&
                    (isUnderJarDirectory(file.getUrl()) || isUnderJarDirectory(moveEvent.getOldParent().getUrl() + "/" + file.getName()))) {
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
      cleanup();
    }
  }

  protected void fireRootSetChanged() {
    myRootProvider.fireRootSetChanged();
  }

  @Override
  public void dispose() {
    cleanup();
  }

  private void cleanup() {
    if (!myWatchRequests.isEmpty()) {
      LocalFileSystem.getInstance().removeWatchedRoots(myWatchRequests);
      myWatchRequests = Collections.emptySet();
    }

    final MessageBusConnection connection = myBusConnection;
    if (connection != null) {
      myBusConnection = null;
      connection.disconnect();
    }
  }
}
