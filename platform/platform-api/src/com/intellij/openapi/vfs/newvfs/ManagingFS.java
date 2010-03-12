/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.CachedSingletonsRegistry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;

public abstract class ManagingFS implements FileSystemInterface {

  private static class ManagingFSHolder {
    private static final ManagingFS ourInstance = ApplicationManager.getApplication().getComponent(ManagingFS.class);
  }

  public static ManagingFS getInstance() {
    return ManagingFSHolder.ourInstance;
  }

  @Nullable
  public abstract DataInputStream readAttribute(VirtualFile file, FileAttribute att);

  public abstract DataOutputStream writeAttribute(VirtualFile file, FileAttribute att);

  public abstract int getModificationCount(VirtualFile fileOrDirectory);

  // Only counts modifications done in current IDEA session
  public abstract int getCheapFileSystemModificationCount();

  public abstract int getFilesystemModificationCount();

  public abstract boolean areChildrenLoaded(VirtualFile dir);

  public abstract boolean wereChildrenAccessed(VirtualFile dir);

  public abstract void processEvents(List<? extends VFileEvent> events);

  @Nullable
  public abstract NewVirtualFile findRoot(final String basePath, NewVirtualFileSystem fs);

  public abstract void refresh(final boolean asynchronous);

  public abstract VirtualFile[] getRoots(final NewVirtualFileSystem fs);


  public abstract VirtualFile[] getLocalRoots();

  @Nullable
  public abstract VirtualFile findFileById(int id);

  public abstract VirtualFile[] getRoots();
}
