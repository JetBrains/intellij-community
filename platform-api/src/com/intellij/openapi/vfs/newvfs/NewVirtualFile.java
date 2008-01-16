/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

public abstract class NewVirtualFile extends VirtualFile {
  private volatile long myModificationStamp = LocalTimeCounter.currentTime();

  public boolean isValid() {
    return exists();
  }

  public byte[] contentsToByteArray() throws IOException {
    final InputStream is = getInputStream();
    final byte[] bytes;
    try {
      bytes = FileUtil.adaptiveLoadBytes(is);
    }
    finally {
      is.close();
    }
    return bytes;
  }

  @NotNull
  public abstract NewVirtualFileSystem getFileSystem();

  public abstract NewVirtualFile getParent();

  @Nullable
  public abstract NewVirtualFile findChild(@NotNull @NonNls final String name);

  @Nullable
  public abstract NewVirtualFile refreshAndFindChild(final String name);

  @Nullable
  public abstract NewVirtualFile findChildIfCached(final String name);


  public abstract void setTimeStamp(final long time) throws IOException;

  public abstract int getId();

  public void refresh(final boolean asynchronous, final boolean recursive, final Runnable postRunnable) {
    RefreshQueue.getInstance().refresh(asynchronous, recursive, postRunnable, this);
  }

  public long getModificationStamp() {
    return myModificationStamp;
  }

  public void setModificationStamp(long modificationStamp) {
    myModificationStamp = modificationStamp;
  }

  public abstract void setWritable(boolean writable) throws IOException;

  public abstract void markDirty();

  public abstract void markDirtyRecursively();

  public abstract boolean isDirty();

  public abstract void markClean();

  public void move(final Object requestor, final VirtualFile newParent) throws IOException {
    if (!exists()) {
      throw new IOException("File to move does not exist: " + getPath());
    }

    if (!newParent.exists()) {
      throw new IOException("Destination folder does not exist: " + newParent.getPath());
    }

    if (!newParent.isDirectory()) {
      throw new IOException("Destination is not a folder: " + newParent.getPath());
    }

    final VirtualFile child = newParent.findChild(getName());
    if (child != null) {
      throw new IOException("Destination already exists: " + newParent.getPath() + "/" + getName());
    }

    VfsUtil.doActionAndRestoreEncoding(this, new ThrowableComputable<VirtualFile, IOException>() {
      public VirtualFile compute() throws IOException {
        getFileSystem().moveFile(requestor, NewVirtualFile.this, newParent);
        return NewVirtualFile.this;
      }
    });
  }

  public abstract Collection<VirtualFile> getCachedChildren();
}