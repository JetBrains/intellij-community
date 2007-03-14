package com.intellij.localvcsintegr;


import com.intellij.localvcs.Label;
import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.LongContent;
import com.intellij.localvcs.Storage;
import com.intellij.localvcs.integration.LocalVcsAction;
import com.intellij.openapi.command.CommandProcessor;
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

  public void ignoreTestProcessingCommands() throws Exception {
    // TODO unignore after rework!!!
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

    assertEquals(1, getVcs().getLabelsFor(f[0].getPath()).size());
  }

  public void testProvidingContent() throws Exception {
    VirtualFile f = root.createChildData(null, "file.java");
    f.setBinaryContent(new byte[]{1});

    assertEquals(1, f.contentsToByteArray()[0]);

    getVcs().changeFileContent(f.getPath(), new byte[]{2}, null);
    getVcs().apply();
    assertEquals(2, f.contentsToByteArray()[0]);
  }

  public void testContentForFilteredFiles() throws Exception {
    VirtualFile f = root.createChildData(null, "file.exe");
    f.setBinaryContent(new byte[]{1});

    assertFalse(vcsHasEntryFor(f));
    assertEquals(1, f.contentsToByteArray()[0]);
  }

  public void testContentForBigFiles() throws Exception {
    VirtualFile f = root.createChildData(null, "file.java");

    byte[] c = new byte[LongContent.MAX_LENGTH + 1];
    c[0] = 7;
    f.setBinaryContent(c);

    assertEquals(7, f.contentsToByteArray()[0]);
  }

  public void testActions() throws Exception {
    VirtualFile f = root.createChildData(null, "file.java");

    f.setBinaryContent(new byte[]{0});
    setDocumentTextFor(f, new byte[]{1});

    assertEquals(0, vcsContentOf(f)[0]);

    LocalVcsAction a = getVcsComponent().startAction("label");
    assertEquals(1, vcsContentOf(f)[0]);

    setDocumentTextFor(f, new byte[]{2});

    a.finish();
    assertEquals(2, vcsContentOf(f)[0]);

    List<Label> l = getVcs().getLabelsFor(f.getPath());
    assertEquals("label", l.get(0).getName());
    assertNull(l.get(1).getName());
  }
}
