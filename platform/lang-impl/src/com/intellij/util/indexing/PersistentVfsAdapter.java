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
package com.intellij.util.indexing;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class PersistentVfsAdapter extends AbstractVfsAdapter {
  private final PersistentFS myFs;

  public PersistentVfsAdapter(PersistentFS fs) {
    myFs = fs;
  }

  @Nullable
  public VirtualFile findFileById(final int id) {
    if (ourUnitTestMode) {
      final VirtualFile testFile = findTestFile(id);
      if (testFile != null) {
        return testFile;
      }
    }

    return myFs.findFileById(id);

    /*

    final boolean isDirectory = fs.isDirectory(id);
    final DirectoryInfo directoryInfo = isDirectory ? dirIndex.getInfoForDirectoryId(id) : dirIndex.getInfoForDirectoryId(fs.getParent(id));
    if (directoryInfo != null && (directoryInfo.contentRoot != null || directoryInfo.sourceRoot != null || directoryInfo.libraryClassRoot != null)) {
      return isDirectory? directoryInfo.directory : directoryInfo.directory.findChild(fs.getName(id));
    }
    return null;
    */
  }


  @Nullable
  public VirtualFile findFileByIdIfCached(final int id) {
    if (ourUnitTestMode) {
      final VirtualFile testFile = findTestFile(id);
      if (testFile != null) {
        return testFile;
      }
    }
    return myFs.findFileByIdIfCached(id);
  }

  @Override
  public boolean wereChildrenAccessed(VirtualFile file) {
    return myFs.wereChildrenAccessed(file);
  }

  @Override
  public Iterable<VirtualFile> getChildren(VirtualFile file) {
    return file instanceof NewVirtualFile ? ((NewVirtualFile)file).iterInDbChildren() : Arrays.asList(file.getChildren());
  }

  @Override
  public boolean getFlag(VirtualFile file, int flag) {
    return file instanceof NewVirtualFile && ((NewVirtualFile)file).getFlag(flag);
  }

  @Override
  public void setFlag(VirtualFile file, int flag, boolean value) {
    if (file instanceof NewVirtualFile)
      ((NewVirtualFile)file).setFlag(flag, value);
  }

  @Override
  public VirtualFile[] getRoots() {
    return myFs.getRoots();
  }

  @Override
  public void iterateCachedFilesRecursively(VirtualFile root, VirtualFileVisitor visitor) {
    final VirtualFile[] roots =  root == null ? myFs.getRoots() : new VirtualFile[] { root };
    for (VirtualFile file : roots) {
      iterate(file, visitor);
    }
  }

  @Override
  public boolean isMock(VirtualFile file) {
    return !(file instanceof NewVirtualFile);
  }

  @Override
  public IndexableFileSet getAdditionalIndexableFileSet() {
    return new AdditionalIndexableFileSet();
  }

  private static void iterate(final VirtualFile file, VirtualFileVisitor visitor) {
    if (!(file instanceof NewVirtualFile)) return;

    final NewVirtualFile nvf = (NewVirtualFile)file;
    if (file.isDirectory()) {
      for (VirtualFile child : nvf.getCachedChildren()) {
        iterate(child, visitor);
      }
    }
    else {
      visitor.visitFile(file);
    }
  }

  @Nullable
  private static VirtualFile findTestFile(final int id) {
    return DummyFileSystem.getInstance().findById(id);
  }
}
