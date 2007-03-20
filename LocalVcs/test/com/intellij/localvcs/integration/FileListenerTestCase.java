package com.intellij.localvcs.integration;

import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.LocalVcsTestCase;
import com.intellij.localvcs.TestLocalVcs;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileMoveEvent;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import static org.easymock.EasyMock.expect;
import static org.easymock.classextension.EasyMock.createMock;
import static org.easymock.classextension.EasyMock.replay;
import org.junit.Before;

public class FileListenerTestCase extends LocalVcsTestCase {
  LocalVcs vcs = new TestLocalVcs();
  TestIdeaGateway gateway;
  FileListener l;

  @Before
  public void setUp() {
    gateway = new TestIdeaGateway();
    l = new FileListener(vcs, gateway, createInitialStateHolder(gateway));
  }

  private ServiceStateHolder createInitialStateHolder(IdeaGateway gw) {
    ServiceStateHolder h = new ServiceStateHolder();
    h.setState(new ListeningServiceState(h, vcs, gw));
    return h;
  }

  protected CommandEvent createCommandEvent(String name) {
    CommandEvent e = createMock(CommandEvent.class);
    expect(e.getCommandName()).andReturn(name);
    replay(e);
    return e;
  }

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
