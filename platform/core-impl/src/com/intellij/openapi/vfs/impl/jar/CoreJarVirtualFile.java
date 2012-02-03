/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.impl.jar;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * @author yole
 */
public class CoreJarVirtualFile extends VirtualFile {
  private final CoreJarHandler myHandler;
  private final VirtualFile myParent;
  private final ArrayList<VirtualFile> myChildren = new ArrayList<VirtualFile>();
  private final JarHandlerBase.EntryInfo myEntry;

  public CoreJarVirtualFile(CoreJarHandler handler, JarHandlerBase.EntryInfo entry, CoreJarVirtualFile parent) {
    myHandler = handler;
    myParent = parent;
    myEntry = entry;

    if (parent != null) {
      parent.myChildren.add(this);
    }
  }

  @NotNull
  @Override
  public String getName() {
    return myEntry.shortName;
  }

  @NotNull
  @Override
  public VirtualFileSystem getFileSystem() {
    return myHandler.getFileSystem();
  }

  @Override
  public String getPath() {
    if (myParent == null) return myHandler.myBasePath + "!/";

    String parentPath = myParent.getPath();
    StringBuilder answer = new StringBuilder(parentPath.length() + 1 + myEntry.shortName.length());
    answer.append(parentPath);
    if (answer.charAt(answer.length() - 1) != '/') {
      answer.append('/');
    }
    answer.append(myEntry.shortName);
    
    return answer.toString();
  }

  @Override
  public boolean isWritable() {
    return false;
  }

  @Override
  public boolean isDirectory() {
    return myEntry.isDirectory;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public VirtualFile getParent() {
    return myParent;
  }

  @Override
  public VirtualFile[] getChildren() {
    return myChildren.toArray(new VirtualFile[myChildren.size()]);
  }

  @NotNull
  @Override
  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    throw new UnsupportedOperationException("JarFileSystem is read-only");
  }

  @NotNull
  @Override
  public byte[] contentsToByteArray() throws IOException {
    return myHandler.contentsToByteArray(this);
  }

  @Override
  public long getTimeStamp() {
    return myHandler.getTimeStamp(this);
  }

  @Override
  public long getLength() {
    return myHandler.getLength(this);
  }

  @Override
  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return myHandler.getInputStream(this);
  }

  @Override
  public long getModificationStamp() {
    return 0;
  }
}
