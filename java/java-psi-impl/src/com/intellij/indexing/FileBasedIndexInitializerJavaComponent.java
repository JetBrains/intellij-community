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
package com.intellij.indexing;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.util.indexing.*;
import org.jetbrains.annotations.NotNull;

public class FileBasedIndexInitializerJavaComponent extends FileBasedIndexInitializer implements ApplicationComponent
{
  public FileBasedIndexInitializerJavaComponent(FileBasedIndex fileBasedIndex,
                                               FileBasedIndexIndicesManager indexIndicesManager,
                                               AbstractVfsAdapter vfsAdapter,
                                               final VirtualFileManagerEx vfManager,
                                               IndexingStamp indexingStamp,
                                               FileBasedIndexLimitsChecker limitsChecker,
                                               FileDocumentManager fileDocumentManager) {
    super(fileBasedIndex, indexIndicesManager, vfsAdapter, vfManager, indexingStamp, limitsChecker, fileDocumentManager);
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "com.intellij.indexing.FileBasedIndexInitializerJavaComponent";
  }

  @Override
  public void disposeComponent() {
    super.disposeComponent();
  }

  @Override
  public void initComponent() {
    super.initComponent();
  }
}
