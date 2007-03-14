package com.intellij.localvcs.integration;

import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.LocalVcsTestCase;
import com.intellij.localvcs.TestLocalVcs;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileMoveEvent;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;

public class FileListenerTestCase extends LocalVcsTestCase {
  LocalVcs vcs = new TestLocalVcs();
  FileListener l = new FileListener(vcs, new TestIdeaGateway(), new TestFileFilter());

  protected void fireCreated(VirtualFile f) {
    l.fileCreated(new VirtualFileEvent(null, f, null, null));
  }

  protected void fireContentChanged(VirtualFile f) {
    l.contentsChanged(new VirtualFileEvent(null, f, null, null));
  }

  protected void fireRenamed(VirtualFile newFile, String oldName) {
    firePropertyChanged(newFile, VirtualFile.PROP_NAME, oldName);
  }

  protected void firePropertyChanged(VirtualFile f, String prop, String oldValue) {
    l.propertyChanged(new VirtualFilePropertyEvent(null, f, prop, oldValue, null));
  }

  protected void fireMoved(VirtualFile f, VirtualFile oldParent, VirtualFile newParent) {
    l.fileMoved(new VirtualFileMoveEvent(null, f, oldParent, newParent));
  }

  protected void fireDeleted(VirtualFile f, VirtualFile parent) {
    l.fileDeleted(new VirtualFileEvent(null, f, null, parent));
  }
}
