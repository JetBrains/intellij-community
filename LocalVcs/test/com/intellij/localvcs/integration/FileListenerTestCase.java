package com.intellij.localvcs.integration;

import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.TestLocalVcs;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;

public class FileListenerTestCase extends MockedLocalFileSystemTestCase {
  LocalVcs vcs = new TestLocalVcs();
  FileListener l;

  protected void fireCreation(VirtualFile f) {
    l.fileCreated(new VirtualFileEvent(null, f, null, null));
  }

  protected void fireRenamed(String oldName, VirtualFile newFile) {
    l.propertyChanged(new VirtualFilePropertyEvent(null, newFile, VirtualFile.PROP_NAME, oldName, null));
  }

  protected void fireDeletion(VirtualFile f, VirtualFile parent) {
    l.fileDeleted(new VirtualFileEvent(null, f, null, parent));
  }
}
