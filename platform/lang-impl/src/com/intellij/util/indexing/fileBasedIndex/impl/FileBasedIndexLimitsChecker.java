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
package com.intellij.util.indexing.fileBasedIndex.impl;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.SingleRootFileViewProvider;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

public class FileBasedIndexLimitsChecker implements ApplicationComponent {
  private final Set<FileType> myNoLimitCheckTypes = new THashSet<FileType>();

  public boolean isTooLarge(VirtualFile file) {
    if (SingleRootFileViewProvider.isTooLarge(file)) {
      final FileType type = file.getFileType();
      return !myNoLimitCheckTypes.contains(type);
    }
    return false;
  }

  public boolean isTooLarge(VirtualFile file, long contentSize) {
    if (SingleRootFileViewProvider.isTooLarge(file, contentSize)) {
      final FileType type = file.getFileType();
      return !myNoLimitCheckTypes.contains(type);
    }
    return false;
  }

  public void addNoLimitsFileTypes(Collection<FileType> types) {
    myNoLimitCheckTypes.addAll(types);
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "FileBasedIndexLimitsChecker";
  }
}
