package com.intellij.openapi.diff.impl.external;

import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.vfs.VirtualFile;

class ExtCompareFolders extends BaseExternalTool {
  public static final BaseExternalTool INSTANCE = new ExtCompareFolders();
  private ExtCompareFolders() {
    super(DiffManagerImpl.ENABLE_FOLDERS, DiffManagerImpl.FOLDERS_TOOL);
  }

  protected BaseExternalTool.ContentExternalizer externalize(DiffRequest request, int index) {
    VirtualFile file = request.getContents()[index].getFile();
    if (!isLocalDirectory(file)) return null;
    return LocalFileExternalizer.tryCreate(file);
  }

  private boolean isLocalDirectory(VirtualFile file) {
    file = getLocalFile(file);
    return file != null && file.isDirectory();
  }

}
