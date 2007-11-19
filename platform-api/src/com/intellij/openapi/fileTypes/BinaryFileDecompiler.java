/*
 * @author max
 */
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.vfs.VirtualFile;

public interface BinaryFileDecompiler {
  CharSequence decompile(VirtualFile file);
}