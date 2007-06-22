package com.intellij.history.integration;

import com.intellij.history.core.InMemoryLocalVcs;
import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.LocalVcsTestCase;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileMoveEvent;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import static org.easymock.EasyMock.expect;
import static org.easymock.classextension.EasyMock.createMock;
import static org.easymock.classextension.EasyMock.replay;
import org.junit.Before;

public class EventDispatcherTestCase extends LocalVcsTestCase {
  LocalVcs vcs = new InMemoryLocalVcs();
  Project project;
  TestIdeaGateway gateway;
  EventDispatcher d;

  @Before
  public void setUp() {
    project = createMock(Project.class);
    gateway = new TestIdeaGateway(project);
    d = new EventDispatcher(vcs, gateway);
  }

  protected CommandEvent createCommandEvent() {
    return createCommandEvent(null, project);
  }

  protected CommandEvent createCommandEvent(String name) {
    return createCommandEvent(name, project);
  }

  protected CommandEvent createCommandEvent(String name, Project p) {
    CommandEvent e = createMock(CommandEvent.class);
    expect(e.getProject()).andStubReturn(p);
    expect(e.getCommandName()).andStubReturn(name);
    replay(e);
    return e;
  }

  protected void fireCreated(VirtualFile f) {
    d.fileCreated(new VirtualFileEvent(null, f, null, null));
  }

  protected void fireCreated(VirtualFile f, Object requestor) {
    d.fileCreated(new VirtualFileEvent(requestor, f, null, null));
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

  protected void fireDeletion(VirtualFile f) {
    fireDeletion(f, null);
  }

  protected void fireDeletion(VirtualFile f, VirtualFile parent) {
    d.fileDeleted(new VirtualFileEvent(null, f, null, parent));
  }
}
