package com.intellij.localvcs.integration;

import com.intellij.localvcs.core.tree.Entry;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Ignore;
import org.junit.Test;

public class LocalHistoryServiceFileListeningTest extends LocalHistoryServiceTestCase {
  @Test
  public void testListening() {
    VirtualFile f = new TestVirtualFile("file", "content", 123L);
    fileManager.fireFileCreated(f);

    Entry e = vcs.findEntry("file");
    assertNotNull(e);

    assertFalse(e.isDirectory());

    assertEquals(c("content"), e.getContent());
    assertEquals(123L, e.getTimestamp());
  }

  @Test
  @Ignore("is this test valuable?")
  public void testDoesNotListenBeforeStartup() {
    initWithoutStartup(createLocalVcs());

    VirtualFile f = new TestVirtualFile("file");
    fileManager.fireFileCreated(f);

    assertFalse(vcs.hasEntry("file"));
  }

  @Test
  public void testUnsubscribingFromFileManagerRefreshEventsOnShutdown() {
    assertTrue(fileManager.hasVirtualFileManagerListener());
    service.shutdown();
    assertFalse(fileManager.hasVirtualFileManagerListener());
  }

  @Test
  public void testUnsubscribingFromFileManagerOnShutdown() {
    assertTrue(fileManager.hasVirtualFileListener());
    service.shutdown();
    assertFalse(fileManager.hasVirtualFileListener());

    VirtualFile f = new TestVirtualFile("file", null, -1);
    fileManager.fireFileCreated(f);

    assertFalse(vcs.hasEntry("file"));
  }
}
