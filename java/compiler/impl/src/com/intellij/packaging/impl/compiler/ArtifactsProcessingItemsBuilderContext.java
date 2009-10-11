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
package com.intellij.packaging.impl.compiler;

import com.intellij.compiler.impl.packagingCompiler.DestinationInfo;
import com.intellij.compiler.impl.packagingCompiler.ProcessingItemsBuilderContext;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.elements.ArtifactIncrementalCompilerContext;

import java.util.Collection;

/**
 * @author nik
 */
public class ArtifactsProcessingItemsBuilderContext extends ProcessingItemsBuilderContext<ArtifactPackagingProcessingItem> implements ArtifactIncrementalCompilerContext {
  private boolean myCollectingEnabledItems;

  public ArtifactsProcessingItemsBuilderContext(CompileContext compileContext) {
    super(compileContext);
  }

  public void addDestination(VirtualFile sourceFile, DestinationInfo destinationInfo) {
    if (checkOutputPath(destinationInfo.getOutputPath(), sourceFile)) {
      getOrCreateProcessingItem(sourceFile).addDestination(destinationInfo, myCollectingEnabledItems);
    }
  }

  protected ArtifactPackagingProcessingItem createProcessingItem(VirtualFile sourceFile) {
    return new ArtifactPackagingProcessingItem(sourceFile);
  }

  public ArtifactPackagingProcessingItem[] getProcessingItems() {
    final Collection<ArtifactPackagingProcessingItem> processingItems = myItemsBySource.values();
    return processingItems.toArray(new ArtifactPackagingProcessingItem[processingItems.size()]);
  }

  public void setCollectingEnabledItems(boolean collectingEnabledItems) {
    myCollectingEnabledItems = collectingEnabledItems;
  }
}
