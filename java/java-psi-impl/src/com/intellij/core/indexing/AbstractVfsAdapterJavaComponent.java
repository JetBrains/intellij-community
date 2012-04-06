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
package com.intellij.core.indexing;


import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.local.CoreLocalFileSystemWithId;
import com.intellij.openapi.vfs.local.CoreLocalVirtualFileWithId;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.indexing.AbstractVfsAdapter;
import com.intellij.util.indexing.IndexableFileSet;
import com.intellij.util.io.UnsyncByteArrayInputStream;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class AbstractVfsAdapterJavaComponent extends AbstractVfsAdapter {
  private CoreLocalFileSystemWithId myFileSystemWithId;
  private HashMap<VirtualFile, byte[]> myAttribute = new HashMap<VirtualFile, byte[]>();

  public AbstractVfsAdapterJavaComponent(CoreLocalFileSystemWithId fileSystemWithId) {
    myFileSystemWithId = fileSystemWithId;
  }

  @Override
  public VirtualFile findFileById(int id) {
    return myFileSystemWithId.findFileById(id);
  }

  @Override
  public VirtualFile findFileByIdIfCached(int id) {
    return myFileSystemWithId.findFileById(id);
  }

  @Override
  public boolean wereChildrenAccessed(VirtualFile file) {
    return true;
  }

  @Override
  public Iterable<VirtualFile> getChildren(VirtualFile file) {
    return Arrays.asList(file.getChildren());
  }

  @Override
  public boolean getFlag(VirtualFile file, int flag) {
    return ((CoreLocalVirtualFileWithId)file).getFlag(flag);
  }

  @Override
  public void setFlag(VirtualFile file, int flag, boolean value) {
    ((CoreLocalVirtualFileWithId)file).setFlag(flag, value);
  }

  @Override
  public void iterateCachedFilesRecursively(VirtualFile root, VirtualFileVisitor visitor) {
    throw new NotImplementedException();

    //final VirtualFile[] roots =  root == null ? myFs.getRoots() : new VirtualFile[] { root };
    //for (VirtualFile file : roots) {
    //  iterate(file, visitor);
    //}
  }

  //private static void iterate(final VirtualFile file, VirtualFileVisitor visitor) {
  //  if (!(file instanceof NewVirtualFile)) return;
  //
  //  final NewVirtualFile nvf = (NewVirtualFile)file;
  //  if (file.isDirectory()) {
  //    for (VirtualFile child : nvf.getCachedChildren()) {
  //      iterate(child, visitor);
  //    }
  //  }
  //  else {
  //    visitor.visitFile(file);
  //  }
  //}

  @Override
  public boolean isMock(VirtualFile file) {
    return false;
  }

  @Override
  public IndexableFileSet getAdditionalIndexableFileSet() {
    return null;
  }

  @Override
  public DataInputStream readTimeStampAttribute(VirtualFile key) {
    final byte[] bytes = myAttribute.get(key);
    if(bytes == null)
      return null;

    return new DataInputStream(new UnsyncByteArrayInputStream(bytes));
  }

  @Override
  public DataOutputStream writeTimeStampAttribute(VirtualFile key) {
        return new AttributeStream(key);
  }

  private class AttributeStream extends com.intellij.util.io.DataOutputStream {
    private VirtualFile myFile;

    private AttributeStream(VirtualFile file) {
      super(new BufferExposingByteArrayOutputStream());
      myFile = file;
    }

    @Override
    public void close() throws IOException {
      super.close();
      doFlush();
    }

    protected void doFlush() throws IOException {
      final BufferExposingByteArrayOutputStream _out = (BufferExposingByteArrayOutputStream)out;
      myAttribute.put(myFile, _out.getInternalBuffer());
    }
  }
}
