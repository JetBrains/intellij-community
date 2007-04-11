package com.intellij.localvcsintegr;


import com.intellij.localvcs.core.revisions.Revision;
import com.intellij.localvcs.integration.LocalVcsAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.List;

public class ActionsTest extends IntegrationTestCase {
  public void testActions() throws Exception {
    VirtualFile f = root.createChildData(null, "f.java");

    f.setBinaryContent(new byte[]{0});
    assertEquals(0, getVcsContentOf(f)[0]);

    setDocumentTextFor(f, new byte[]{1});

    assertEquals(0, getVcsContentOf(f)[0]);

    LocalVcsAction a = getVcsComponent().startAction("name");
    assertEquals(1, getVcsContentOf(f)[0]);

    setDocumentTextFor(f, new byte[]{2});

    a.finish();
    assertEquals(2, getVcsContentOf(f)[0]);

    List<Revision> rr = getVcsRevisionsFor(f);
    assertEquals(4, rr.size());
    assertEquals("name", rr.get(0).getCauseAction());
  }

  public void testActionInsideCommand() throws Exception {
    final VirtualFile f = root.createChildData(null, "f.java");
    f.setBinaryContent(new byte[]{0});
    setDocumentTextFor(f, new byte[]{1});

    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        LocalVcsAction a = getVcsComponent().startAction("action");
        setDocumentTextFor(f, new byte[]{2});
        a.finish();
      }
    }, "command", null);

    List<Revision> rr = getVcsRevisionsFor(f);
    assertEquals(4, rr.size());
    assertEquals("command", rr.get(0).getCauseAction());

    assertEquals(2, contentOf(rr.get(0))[0]);
    assertEquals(1, contentOf(rr.get(1))[0]);
    assertEquals(0, contentOf(rr.get(2))[0]);
    assertTrue(contentOf(rr.get(3)).length == 0);
  }

  public void testActionInsideCommandSurroundedWithSomeChanges() throws Exception {
    final VirtualFile f = root.createChildData(null, "f.java");

    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        try {
          f.setBinaryContent(new byte[]{0});
          setDocumentTextFor(f, new byte[]{1});

          LocalVcsAction a = getVcsComponent().startAction("action");
          setDocumentTextFor(f, new byte[]{2});
          a.finish();

          saveDocument(f);
          f.setBinaryContent(new byte[]{3});
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }, "command", null);

    List<Revision> rr = getVcsRevisionsFor(f);
    assertEquals(3, rr.size());

    assertEquals(3, contentOf(rr.get(0))[0]);
    assertEquals(1, contentOf(rr.get(1))[0]);
    assertTrue(contentOf(rr.get(2)).length == 0);

    assertEquals("command", rr.get(0).getCauseAction());
    assertNull(rr.get(1).getCauseAction());
    assertNull(rr.get(2).getCauseAction());
  }

  private void saveDocument(VirtualFile f) {
    FileDocumentManager dm = FileDocumentManager.getInstance();
    dm.saveDocument(dm.getDocument(f));
  }

  private byte[] contentOf(Revision r) {
    return r.getEntry().getContent().getBytes();
  }
}