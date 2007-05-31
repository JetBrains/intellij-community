package com.intellij.localvcs.integration;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class LocalHistoryServiceRootsOnStartupTest extends LocalHistoryServiceTestCase {
  // todo what about roots in jars (non-local file system)?

  @Before
  public void setUp() {
    initWithoutStartup(createLocalVcs());
  }

  @Test
  public void testUpdatingRootsOnStartup() {
    roots.add(new TestVirtualFile("c:/root"));
    startupService();

    assertTrue(vcs.hasEntry("c:/root"));
  }

  @Test
  public void testAddingNewFiles() {
    TestVirtualFile root = new TestVirtualFile("c:/root");
    root.addChild(new TestVirtualFile("file", "", -1));
    roots.add(root);

    startupService();

    assertTrue(vcs.hasEntry("c:/root/file"));
  }

  @Test
  public void testUpdatingOutdatedFiles() {
    vcs.createDirectory("c:/root");
    vcs.createFile("c:/root/file", cf("old"), 111L);

    TestVirtualFile root = new TestVirtualFile("c:/root");
    root.addChild(new TestVirtualFile("file", "new", 222L));
    roots.add(root);

    startupService();

    assertEquals(c("new"), vcs.getEntry("c:/root/file").getContent());
  }

  @Test
  public void testDeleteObsoleteFiles() {
    vcs.createDirectory("c:/root");
    vcs.createFile("c:/root/file", null, -1);

    roots.add(new TestVirtualFile("c:/root"));
    startupService();

    assertFalse(vcs.hasEntry("c:/root/file"));
  }

  @Test
  public void testDoesNotUpdateRootsBeforeStartupActivity() {
    roots.add(new TestVirtualFile("c:/root"));
    initWithoutStartup(createLocalVcs());

    assertFalse(vcs.hasEntry("c:/root"));
  }

  @Test
  @Ignore
  public void testDoesNotTrackRootChangesBeforeStartup() {
    // todo are we sure?
    roots.add(new TestVirtualFile("c:/root"));
    rootManager.updateRoots();

    assertFalse(vcs.hasEntry("c:/root"));
  }

  @Test
  public void testUnsubscribingFromRootChangesOnShutdown() {
    initAndStartup(createLocalVcs());
    service.shutdown();

    roots.add(new TestVirtualFile("root"));
    rootManager.updateRoots();

    assertFalse(vcs.hasEntry("root"));
  }
}
