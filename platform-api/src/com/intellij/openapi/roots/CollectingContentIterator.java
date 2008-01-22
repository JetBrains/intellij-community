/*
 * @author max
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

public interface CollectingContentIterator extends ContentIterator {
  List<VirtualFile> getFiles();
}