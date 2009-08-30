package com.intellij.packaging.impl.compiler;

import com.intellij.packaging.elements.IncrementalCompilerInstructionCreator;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author nik
 */
public abstract class IncrementalCompilerInstructionCreatorBase implements IncrementalCompilerInstructionCreator {
  public void addDirectoryCopyInstructions(VirtualFile directory) {
    final VirtualFile[] children = directory.getChildren();
    if (children != null) {
      for (VirtualFile child : children) {
        if (!child.isDirectory()) {
          addFileCopyInstruction(child, child.getName());
        }
        else {
          subFolder(child.getName()).addDirectoryCopyInstructions(child);
        }
      }
    }
  }
}
