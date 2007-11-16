package com.intellij.historyIntegrTests;


import com.intellij.history.Clock;
import com.intellij.history.LocalHistory;
import com.intellij.history.FileRevisionTimestampComparator;
import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.TestTimestampComparator;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.storage.Storage;
import com.intellij.history.utils.RunnableAdapter;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class BasicsTest extends IntegrationTestCase {
  public void testComponentInitialization() {
    assertNotNull(getVcsComponent());
  }

  public void testSaving() throws Exception {
    VirtualFile f = root.createChildData(null, "file.java");
    myProject.save();
    getVcsComponent().closeVcs();

    File dir = getVcsComponent().getStorageDir();
    Storage s = new Storage(dir);
    LocalVcs vcs = new LocalVcs(s);
    s.close();
    assertTrue(vcs.hasEntry(f.getPath()));
  }

  public void testProcessingCommands() throws Exception {
    final VirtualFile[] f = new VirtualFile[1];

    CommandProcessor.getInstance().executeCommand(myProject, new RunnableAdapter() {
      @Override
      public void doRun() throws IOException {
        f[0] = root.createChildData(null, "f1.java");
        f[0].setBinaryContent(new byte[]{1});
        f[0].setBinaryContent(new byte[]{2});
      }
    }, "name", null);

    assertEquals(1, getVcsRevisionsFor(f[0]).size());
  }

  public void testUpdatingOnFileTypesChange() throws Exception {
    VirtualFile f = root.createChildData(null, "file.xxx");

    assertFalse(hasVcsEntry(f));

    FileTypeManager tm = FileTypeManager.getInstance();
    tm.registerFileType(FileTypes.PLAIN_TEXT, "xxx");

    assertTrue(hasVcsEntry(f));

    tm.removeAssociatedExtension(FileTypes.PLAIN_TEXT, "xxx");

    assertFalse(hasVcsEntry(f));
  }

  public void testPuttingUserLabel() throws Exception {
    VirtualFile f = root.createChildData(null, "f.java");

    LocalHistory.putUserLabel(myProject, "global");

    assertEquals(2, getVcsRevisionsFor(f).size());
    assertEquals(3, getVcsRevisionsFor(root).size());

    LocalHistory.putUserLabel(myProject, f, "file");

    List<Revision> rr = getVcsRevisionsFor(f);
    assertEquals(3, rr.size());
    assertEquals("file", rr.get(0).getName());
    assertFalse(rr.get(0).getCauseChange().isSystemLabel());
    assertEquals("global", rr.get(1).getName());
    assertFalse(rr.get(1).getCauseChange().isSystemLabel());

    rr = getVcsRevisionsFor(root);
    assertEquals(3, rr.size());
    assertEquals("global", rr.get(0).getName());
    assertFalse(rr.get(0).getCauseChange().isSystemLabel());
  }

  public void testPuttingSystemLabel() throws IOException {
    VirtualFile f = root.createChildData(null, "file.java");

    assertEquals(1, getVcsRevisionsFor(f).size());
    assertEquals(2, getVcsRevisionsFor(root).size());

    LocalHistory.putSystemLabel(myProject, "label");

    List<Revision> rr = getVcsRevisionsFor(f);
    assertEquals(2, rr.size());
    assertEquals("label", rr.get(0).getName());
    assertTrue(rr.get(0).getCauseChange().isSystemLabel());

    rr = getVcsRevisionsFor(root);
    assertEquals(3, rr.size());
    assertEquals("label", rr.get(0).getName());
    assertTrue(rr.get(0).getCauseChange().isSystemLabel());
  }

  public void testPuttingLabelWithUnsavedDocuments() throws Exception {
    VirtualFile f = root.createChildData(null, "f.java");
    f.setBinaryContent(new byte[]{1});

    setDocumentTextFor(f, new byte[] {2});
    LocalHistory.putSystemLabel(myProject, "label");

    assertEquals(2, getVcsContentOf(f)[0]);

    setDocumentTextFor(f, new byte[] {3});
    LocalHistory.putUserLabel(myProject, "label");

    assertEquals(3, getVcsContentOf(f)[0]);

    setDocumentTextFor(f, new byte[] {4});
    LocalHistory.putUserLabel(myProject, f, "label");

    assertEquals(4, getVcsContentOf(f)[0]);
  }

  public void testIsUnderControl() throws Exception {
    VirtualFile f1 = root.createChildData(null, "file.java");
    VirtualFile f2 = root.createChildData(null, "file.xxx");

    assertTrue(LocalHistory.isUnderControl(myProject, f1));
    assertFalse(LocalHistory.isUnderControl(myProject, f2));
  }

  public void testHasUnavailableContent() throws Exception {
    VirtualFile f = root.createChildData(null, "file.java");
    assertFalse(LocalHistory.hasUnavailableContent(myProject, f));

    f.setBinaryContent(new byte[2 * 1024 * 1024]);
    assertTrue(LocalHistory.hasUnavailableContent(myProject, f));
  }

  public void testHasUnavailableContentForUnversionedFile() throws Exception {
    VirtualFile f = root.createChildData(null, "f");
    assertFalse(LocalHistory.hasUnavailableContent(myProject, f));
  }

  public void testHasUnavailableContentForDirectory() throws Exception {
    VirtualFile dir = root.createChildDirectory(null, "dir");
    assertFalse(LocalHistory.hasUnavailableContent(myProject, dir));
  }

  public void testContentAtDate() throws Exception {
    VirtualFile f = root.createChildData(null, "f.java");
    Clock.setCurrentTimestamp(10);
    f.setBinaryContent(new byte[]{1});
    Clock.setCurrentTimestamp(20);
    f.setBinaryContent(new byte[]{2});

    assertEquals(1, LocalHistory.getByteContent(myProject, f, revisionComparator(10))[0]);
    assertNull(LocalHistory.getByteContent(myProject, f, revisionComparator(15)));
    assertEquals(2, LocalHistory.getByteContent(myProject, f, revisionComparator(20))[0]);
    assertNull(LocalHistory.getByteContent(myProject, f, revisionComparator(30)));
  }

  public void testContentAtDateForFilteredFilesIsNull() throws Exception {
    VirtualFile f = root.createChildData(null, "f.xxx");
    Clock.setCurrentTimestamp(10);
    f.setBinaryContent(new byte[]{1});

    assertNull(LocalHistory.getByteContent(myProject, f, revisionComparator(10)));
  }

  public void testRevisionsIfThereWasFileThatBecameUnversioned() throws IOException {
    FileTypeManager.getInstance().registerFileType(StdFileTypes.JAVA, "jjj");
    VirtualFile f = root.createChildData(null, "f.jjj");
    FileTypeManager.getInstance().removeAssociatedExtension(StdFileTypes.JAVA, "jjj");

    List<Revision> rr = getVcsRevisionsFor(root);
    assertEquals(3, rr.size());

    assertNull(rr.get(0).getEntry().findChild("f.jjj"));
    assertNotNull(rr.get(1).getEntry().findChild("f.jjj"));
    assertNull(rr.get(2).getEntry().findChild("f.jjj"));
  }


  private FileRevisionTimestampComparator revisionComparator(long timestamp) {
    return new TestTimestampComparator(-1, timestamp);
  }
}
