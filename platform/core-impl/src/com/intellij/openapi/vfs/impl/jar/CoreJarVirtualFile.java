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
import java.util.List;

/**
 * @author yole
 */
public class CoreJarVirtualFile extends VirtualFile {
  private final CoreJarFileSystem myFileSystem;
  private final CoreJarHandler myHandler;
  private final String myPathInJar;

  public CoreJarVirtualFile(CoreJarFileSystem fileSystem, CoreJarHandler handler, String pathInJar) {
    myFileSystem = fileSystem;
    myHandler = handler;
    myPathInJar = pathInJar;
  }

  @NotNull
  @Override
  public String getName() {
    final int lastSlash = myPathInJar.lastIndexOf('/');
    if (lastSlash < 0) {
      return myPathInJar;
    }
    return myPathInJar.substring(lastSlash+1);
  }

  @NotNull
  @Override
  public VirtualFileSystem getFileSystem() {
    return myFileSystem;
  }

  @Override
  public String getPath() {
    return myHandler.myBasePath + "!/" + myPathInJar;
  }

  @Override
  public boolean isWritable() {
    return false;
  }

  @Override
  public boolean isDirectory() {
    return myHandler.isDirectory(this);
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public VirtualFile getParent() {
    if (myPathInJar.length() == 0) {
      return null;
    }
    int lastSlash = myPathInJar.lastIndexOf('/');
    if (lastSlash < 0) {
      return myHandler.findFileByPath("");
    }
    return myHandler.findFileByPath(myPathInJar.substring(0, lastSlash));
  }

  @Override
  public VirtualFile[] getChildren() {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    final String[] children = myHandler.list(this);
    for (String child : children) {
      final VirtualFile childFile = myPathInJar.isEmpty() ? myHandler.findFileByPath(child) : myHandler.findFileByPath(myPathInJar + "/" + child);
      result.add(childFile);
    }
    return result.toArray(new VirtualFile[result.size()]);
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
