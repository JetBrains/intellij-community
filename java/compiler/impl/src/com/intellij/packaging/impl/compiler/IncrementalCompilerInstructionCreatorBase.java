package com.intellij.packaging.impl.compiler;

import com.intellij.openapi.compiler.make.PackagingFileFilter;
import com.intellij.packaging.elements.IncrementalCompilerInstructionCreator;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class IncrementalCompilerInstructionCreatorBase implements IncrementalCompilerInstructionCreator {
  protected final ArtifactsProcessingItemsBuilderContext myContext;

  public IncrementalCompilerInstructionCreatorBase(ArtifactsProcessingItemsBuilderContext context) {
    myContext = context;
  }

  public void addDirectoryCopyInstructions(@NotNull VirtualFile directory) {
    addDirectoryCopyInstructions(directory, null);
  }

  public void addDirectoryCopyInstructions(@NotNull VirtualFile directory, @Nullable PackagingFileFilter filter) {
    final VirtualFile[] children = directory.getChildren();
    if (children != null) {
      for (VirtualFile child : children) {
        if (filter == null || filter.accept(child, myContext.getCompileContext())) {
          if (!child.isDirectory()) {
            addFileCopyInstruction(child, child.getName());
          }
          else {
            subFolder(child.getName()).addDirectoryCopyInstructions(child, filter);
          }
        }
      }
    }
  }
}
