/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.vfs;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;

import java.io.*;

public abstract class AbstractVcsVirtualFile extends VirtualFile {

  protected final String myName;
  protected final String myPath;
  protected String myRevision;
  private final VirtualFile myParent;
  protected int myModificationStamp = 0;
  private final VirtualFileSystem myFileSystem;
  private static final byte[] EMPTY_BUF = new byte[0];

  protected AbstractVcsVirtualFile(String path, VirtualFileSystem fileSystem) {
    myFileSystem = fileSystem;
    myPath = path;
    File file = new File(myPath);
    myName = file.getName();
    if (!isDirectory())
      myParent = new VcsVirtualFolder(file.getParent(), this, myFileSystem);
    else
      myParent = null;
  }

  public VirtualFileSystem getFileSystem() {
    return myFileSystem;
  }

  public String getPath() {
    return myPath;
  }

  public String getPathInCvs() {
    return myPath;
  }

  public String getName() {
    return myName;
  }

  public String getPresentableName() {
    if (myRevision == null)
      return myName;
    else
      return myName + " (" + myRevision + ")";
  }

  public void rename(Object requestor, String newName) throws IOException {
    throw new RuntimeException("Could not implement");
  }

  public boolean isWritable() {
    return false;
  }

  public boolean isValid() {
    return true;
  }

  public VirtualFile getParent() {
    return myParent;

  }

  public VirtualFile[] getChildren() {
    return null;
  }

  public VirtualFile createChildDirectory(Object requestor, String name) throws IOException {
    throw new RuntimeException("Could not implement");
  }

  public VirtualFile createChildData(Object requestor, String name) throws IOException {
    throw new RuntimeException("Could not implement");
  }

  public void delete(Object requestor) throws IOException {
    throw new RuntimeException("Could not implement");
  }

  public void move(Object requestor, VirtualFile newParent) throws IOException {
    throw new RuntimeException("Could not implement");
  }

  public InputStream getInputStream() throws IOException {
    final byte[] buf = contentsToByteArray();
    //52243 NPE
    if (buf != null) {
      return new ByteArrayInputStream(buf);
    } else {
      return new ByteArrayInputStream(EMPTY_BUF);
    }
  }

  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    throw new RuntimeException("Could not implement");
  }

  public abstract byte[] contentsToByteArray() throws IOException;

  public char[] contentsToCharArray() throws IOException {
    return new String(contentsToByteArray(), getCharset().name()).toCharArray();
  }

  public long getModificationStamp() {
    return myModificationStamp;
  }

  public long getTimeStamp() {
    return myModificationStamp;
  }

  public long getActualTimeStamp() {
    return myModificationStamp;
  }

  public long getLength() {
    try {
      return contentsToByteArray().length;
    } catch (IOException e) {
      return 0;
    }
  }

  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
    if (postRunnable != null)
      postRunnable.run();
  }
}
