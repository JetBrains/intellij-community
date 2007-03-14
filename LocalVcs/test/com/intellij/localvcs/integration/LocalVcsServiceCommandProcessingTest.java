package com.intellij.localvcs.integration;

import org.junit.Ignore;
import org.junit.Test;

public class LocalVcsServiceCommandProcessingTest extends LocalVcsServiceTestCase {
  @Test
  @Ignore
  public void testProcessingCommand() {
    commandProcessor.executeCommand(new Runnable() {
      public void run() {
        fileManager.fireFileCreated(new TestVirtualFile("file", "abc", null));
        fileManager.fireContentChanged(new TestVirtualFile("file", "def", null));
      }
    }, null, null);

    assertTrue(vcs.hasEntry("file"));
    assertEquals(c("def"), vcs.getEntry("file").getContent());
    assertEquals(1, vcs.getLabelsFor("file").size());
  }

  @Test
  public void testUnregisteringCommandListenerOnShutdown() {
    assertTrue(commandProcessor.hasListener());
    service.shutdown();
    assertFalse(commandProcessor.hasListener());
  }
}