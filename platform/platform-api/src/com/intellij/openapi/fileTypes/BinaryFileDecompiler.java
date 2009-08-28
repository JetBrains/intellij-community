/*
 * @author max
 */
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public interface BinaryFileDecompiler {
  @NotNull
  CharSequence decompile(VirtualFile file);
}