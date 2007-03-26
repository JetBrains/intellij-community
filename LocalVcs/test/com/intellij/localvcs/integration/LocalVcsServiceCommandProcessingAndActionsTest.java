package com.intellij.localvcs.integration;

import com.intellij.localvcs.Label;
import org.junit.Test;

import java.util.List;

public class LocalVcsServiceCommandProcessingAndActionsTest extends LocalVcsServiceTestCase {
  @Test
  public void testActions() {
    LocalVcsAction a = service.startAction("label");
    fileManager.fireFileCreated(new TestVirtualFile("f", null, -1));
    a.finish();

    assertTrue(vcs.hasEntry("f"));
    assertEquals("label", vcs.getLabelsFor("f").get(0).getName());
  }

  @Test
  public void testProcessingCommand() {
    commandProcessor.executeCommand(new Runnable() {
      public void run() {
        fileManager.fireFileCreated(new TestVirtualFile("file", "abc", -1));
        fileManager.fireContentChanged(new TestVirtualFile("file", "def", -1));
      }
    }, "command", null);

    assertTrue(vcs.hasEntry("file"));
    assertEquals(c("def"), vcs.getEntry("file").getContent());

    List<Label> ll = vcs.getLabelsFor("file");
    assertEquals(1, ll.size());
    assertEquals("command", ll.get(0).getName());
  }

  @Test
  public void testUnregisteringCommandListenerOnShutdown() {
    assertTrue(commandProcessor.hasListener());
    service.shutdown();
    assertFalse(commandProcessor.hasListener());
  }
}