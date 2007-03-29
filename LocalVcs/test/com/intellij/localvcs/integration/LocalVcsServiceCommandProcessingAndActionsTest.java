package com.intellij.localvcs.integration;

import com.intellij.localvcs.Label;
import org.junit.Test;

import java.util.List;

public class LocalVcsServiceCommandProcessingAndActionsTest extends LocalVcsServiceTestCase {
  @Test
  public void testActions() {
    TestVirtualFile dir = new TestVirtualFile("dir");
    fileManager.fireFileCreated(dir);

    TestVirtualFile one = new TestVirtualFile("one", null, -1);
    TestVirtualFile two = new TestVirtualFile("two", null, -1);
    dir.addChild(one);
    dir.addChild(two);

    LocalVcsAction a = service.startAction("label");
    fileManager.fireFileCreated(one);
    fileManager.fireFileCreated(two);
    a.finish();

    assertTrue(vcs.hasEntry("dir/one"));
    assertTrue(vcs.hasEntry("dir/two"));

    List<Label> ll = vcs.getLabelsFor("dir");
    assertEquals(2, ll.size());
    assertEquals("label", ll.get(0).getName());
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