package com.intellij.localvcs.integration;

import com.intellij.localvcs.core.LocalVcs;
import com.intellij.localvcs.core.LocalVcsTestCase;
import com.intellij.localvcs.core.TestLocalVcs;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileMoveEvent;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import static org.easymock.EasyMock.expect;
import static org.easymock.classextension.EasyMock.createMock;
import static org.easymock.classextension.EasyMock.replay;
import org.junit.Before;

public class EventDispatcherTestCase extends LocalVcsTestCase {
  LocalVcs vcs = new TestLocalVcs();
  TestIdeaGateway gateway;
  EventDispatcher d;

  @Before
  public void setUp() {
    gateway = new TestIdeaGateway();
    d = new EventDispatcher(vcs, gateway);
  }

  protected CommandEvent createCommandEvent(String name) {
    CommandEvent e = createMock(CommandEvent.class);
    expect(e.getCommandName()).andReturn(name);
    replay(e);
    return e;
  }

  protected void fireCreated(VirtualFile f) {
    d.fileCreated(new VirtualFileEvent(null, f, null, null));
  }

  protected void fireContentChanged(VirtualFile f) {
    d.contentsChanged(new VirtualFileEvent(null, f, null, null));
  }

  protected void fireRenamed(VirtualFile newFile, String oldName) {
    firePropertyChanged(newFile, VirtualFile.PROP_NAME, oldName);
  }

  protected void firePropertyChanged(VirtualFile f, String prop, String oldValue) {
    d.propertyChanged(new VirtualFilePropertyEvent(null, f, prop, oldValue, null));
  }

  protected void fireMoved(VirtualFile f, VirtualFile oldParent, VirtualFile newParent) {
    d.fileMoved(new VirtualFileMoveEvent(null, f, oldParent, newParent));
  }

  protected void fireDeleted(VirtualFile f, VirtualFile parent) {
    d.fileDeleted(new VirtualFileEvent(null, f, null, parent));
  }
}
