package com.intellij.openapi.roots.impl;

import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ContentFolder;
import com.intellij.openapi.roots.ExcludedOutputFolder;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;

/**
 *  @author dsl
 */
public class ExcludedOutputFolderImpl extends ContentFolderBaseImpl implements ExcludedOutputFolder, ClonableContentFolder {
  ExcludedOutputFolderImpl(ContentEntryImpl contentEntry, VirtualFilePointer outputPath) {
    super(outputPath, contentEntry);
  }

  public boolean isSynthetic() {
    return true;
  }

  public ContentFolder cloneFolder(final ContentEntry contentEntry) {
    return new ExcludedOutputFolderImpl((ContentEntryImpl)contentEntry, VirtualFilePointerManager.getInstance().create(getUrl(), getRootModel().getModule(),
                                                                                                                       ((ContentEntryImpl)contentEntry).getRootModel().myVirtualFilePointerListener));
  }

  @Override
  public int compareTo(ContentFolderBaseImpl folder) {
    if (!(folder instanceof ExcludedOutputFolderImpl)) return -1;
    return super.compareTo(folder);
  }
}
