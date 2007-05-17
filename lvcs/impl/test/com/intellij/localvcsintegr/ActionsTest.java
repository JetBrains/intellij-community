package com.intellij.localvcsintegr;


import com.intellij.localvcs.core.revisions.Revision;
import com.intellij.localvcs.integration.LocalHistory;
import com.intellij.localvcs.integration.LocalHistoryAction;
import com.intellij.localvcs.utils.RunnableAdapter;
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

    LocalHistoryAction a = LocalHistory.startAction(myProject, "name");
    assertEquals(1, getVcsContentOf(f)[0]);

    setDocumentTextFor(f, new byte[]{2});

    a.finish();
    assertEquals(2, getVcsContentOf(f)[0]);

    List<Revision> rr = getVcsRevisionsFor(f);
    assertEquals(4, rr.size());
    assertEquals("name", rr.get(0).getCauseChangeName());
  }

  public void testActionInsideCommand() throws Exception {
    final VirtualFile f = root.createChildData(null, "f.java");
    f.setBinaryContent(new byte[]{0});
    setDocumentTextFor(f, new byte[]{1});

    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        LocalHistoryAction a = LocalHistory.startAction(myProject, "action");
        setDocumentTextFor(f, new byte[]{2});
        a.finish();
      }
    }, "command", null);

    List<Revision> rr = getVcsRevisionsFor(f);
    assertEquals(4, rr.size());
    assertEquals("command", rr.get(0).getCauseChangeName());

    assertEquals(2, contentOf(rr.get(0))[0]);
    assertEquals(1, contentOf(rr.get(1))[0]);
    assertEquals(0, contentOf(rr.get(2))[0]);
    assertTrue(contentOf(rr.get(3)).length == 0);
  }

  public void testActionInsideCommandSurroundedWithSomeChanges() throws Exception {
    final VirtualFile f = root.createChildData(null, "f.java");

    CommandProcessor.getInstance().executeCommand(myProject, new RunnableAdapter() {
      @Override
      public void doRun() throws IOException {
        f.setBinaryContent(new byte[]{0});
        setDocumentTextFor(f, new byte[]{1});

        LocalHistoryAction a = LocalHistory.startAction(myProject, "action");
        setDocumentTextFor(f, new byte[]{2});
        a.finish();

        saveDocument(f);
        f.setBinaryContent(new byte[]{3});
      }
    }, "command", null);

    List<Revision> rr = getVcsRevisionsFor(f);
    assertEquals(3, rr.size());

    assertEquals(3, contentOf(rr.get(0))[0]);
    assertEquals(1, contentOf(rr.get(1))[0]);
    assertTrue(contentOf(rr.get(2)).length == 0);

    assertEquals("command", rr.get(0).getCauseChangeName());
    assertNull(rr.get(1).getCauseChangeName());
    assertNull(rr.get(2).getCauseChangeName());
  }

  private void saveDocument(VirtualFile f) {
    FileDocumentManager dm = FileDocumentManager.getInstance();
    dm.saveDocument(dm.getDocument(f));
  }

  private byte[] contentOf(Revision r) {
    return r.getEntry().getContent().getBytes();
  }
}