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
