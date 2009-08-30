package com.intellij.compiler.impl;

import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 18, 2003
 */
public abstract class FileIndexCompileScope extends UserDataHolderBase implements CompileScope {

  protected abstract FileIndex[] getFileIndices();

  @NotNull
  public VirtualFile[] getFiles(final FileType fileType, final boolean inSourceOnly) {
    final List<VirtualFile> files = new ArrayList<VirtualFile>();
    final FileIndex[] fileIndices = getFileIndices();
    for (final FileIndex fileIndex : fileIndices) {
      fileIndex.iterateContent(new CompilerContentIterator(fileType, fileIndex, inSourceOnly, files));
    }
    return files.toArray(new VirtualFile[files.size()]);
  }
}
