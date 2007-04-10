package com.intellij.localvcsintegr;


import com.intellij.localvcs.core.LocalVcs;
import com.intellij.localvcs.core.revisions.Revision;
import com.intellij.localvcs.core.storage.Storage;
import com.intellij.localvcs.integration.LocalVcsAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.List;

public class BasicsTest extends IntegrationTestCase {
  public void testComponentInitialization() {
    assertNotNull(getVcsComponent());
  }

  public void testSaving() throws Exception {
    VirtualFile f = root.createChildData(null, "file.java");
    myProject.save();

    Storage s = new Storage(getVcsComponent().getStorageDir());
    LocalVcs vcs = new LocalVcs(s);
    s.close();
    assertTrue(vcs.hasEntry(f.getPath()));
  }

  public void testProcessingCommands() throws Exception {
    final VirtualFile[] f = new VirtualFile[1];

    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        try {
          f[0] = root.createChildData(null, "f1.java");
          f[0].setBinaryContent(new byte[]{1});
          f[0].setBinaryContent(new byte[]{2});
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }, "name", null);

    assertEquals(1, getVcsRevisionsFor(f[0]).size());
  }

  public void testActions() throws Exception {
    VirtualFile f = root.createChildData(null, "file.java");

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
    assertEquals("name", rr.get(0).getCauseAction());
    assertNull(rr.get(1).getName());
  }

  public void testUpdatingOnFileTypesChange() throws Exception {
    VirtualFile f = root.createChildData(null, "file.xxx");

    assertFalse(hasVcsEntry(f));

    FileTypeManager tm = FileTypeManager.getInstance();
    tm.registerFileType(StdFileTypes.PLAIN_TEXT, "xxx");

    assertTrue(hasVcsEntry(f));

    tm.removeAssociatedExtension(StdFileTypes.PLAIN_TEXT, "xxx");

    assertFalse(hasVcsEntry(f));
  }
}
