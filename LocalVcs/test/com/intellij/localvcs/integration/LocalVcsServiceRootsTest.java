package com.intellij.localvcs.integration;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import org.junit.Test;

public class LocalVcsServiceRootsTest extends LocalVcsServiceTestCase {
  // todo what about roots in jars (non-local file system)?

  @Test
  public void testUpdatingRootsOnStartup() {
    initWithoutStartup(createLocalVcs());

    roots.add(new TestVirtualFile("c:/root", null));
    startupService();

    assertTrue(vcs.hasEntry("c:/root"));
  }

  @Test
  public void testDoesNotUpdateRootsOnBeforeStartupActivity() {
    roots.add(new TestVirtualFile("c:/root", null));
    initWithoutStartup(createLocalVcs());

    assertFalse(vcs.hasEntry("c:/root"));
  }

  @Test
  public void testDoesNotTrackChangesBeforeStartup() {
    initWithoutStartup(createLocalVcs());

    roots.add(new TestVirtualFile("c:/root", null));
    rootManager.updateRoots();

    assertFalse(vcs.hasEntry("c:/root"));
  }

  @Test
  public void testUpdatingRootsWithContent() {
    TestVirtualFile root = new TestVirtualFile("c:/root", null);
    root.addChild(new TestVirtualFile("file", "", null));
    roots.add(root);

    rootManager.updateRoots();

    assertTrue(vcs.hasEntry("c:/root"));
    assertTrue(vcs.hasEntry("c:/root/file"));
  }

  @Test
  public void testUpdatingRootsWithFiltering() {
    TestVirtualFile root = new TestVirtualFile("c:/root", null);
    root.addChild(new TestVirtualFile("file", "", null));
    roots.add(root);

    fileFilter.dontAllowAnyFile();
    rootManager.updateRoots();

    assertTrue(vcs.hasEntry("c:/root"));
    assertFalse(vcs.hasEntry("c:/root/file"));
  }

  @Test
  public void testRenamingContentRoot() {
    TestVirtualFile root = new TestVirtualFile("c:/dir/rootName", null);
    root.addChild(new TestVirtualFile("file", "", null));
    roots.add(root);
    rootManager.updateRoots();

    fileManager.fireBeforePropertyChange(new VirtualFilePropertyEvent(null, root, VirtualFile.PROP_NAME, null, "newName"));

    assertFalse(vcs.hasEntry("c:/dir/rootName"));
    assertTrue(vcs.hasEntry("c:/dir/newName"));
    assertTrue(vcs.hasEntry("c:/dir/newName/file"));
  }

  @Test
  public void testDeletingContentRootExternally() {
    TestVirtualFile root = new TestVirtualFile("c:/root", null);
    roots.add(root);
    rootManager.updateRoots();

    fileManager.fireBeforeFileDeletion(new VirtualFileEvent(null, root, null, null));

    assertFalse(vcs.hasEntry("c:/root"));
  }

  @Test
  public void testUnsubscribingFromRootChangesOnShutdown() {
    service.shutdown();

    roots.add(new TestVirtualFile("root", null));
    rootManager.updateRoots();

    assertFalse(vcs.hasEntry("root"));
  }
}
