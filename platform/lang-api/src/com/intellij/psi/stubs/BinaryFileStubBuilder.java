/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

public interface BinaryFileStubBuilder {
  boolean acceptsFile(final VirtualFile file);

  @Nullable
  StubElement buildStubTree(final VirtualFile file, byte[] content, final Project project);

  int getStubVersion();
}