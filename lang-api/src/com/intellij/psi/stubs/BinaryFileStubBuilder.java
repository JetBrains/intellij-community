/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public interface BinaryFileStubBuilder {
  boolean acceptsFile(final VirtualFile file);

  @Nullable
  StubElement buildStubTree(final VirtualFile file, byte[] content);

  int getStubVersion();
}