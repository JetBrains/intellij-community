package com.intellij.localvcs.integration.ui.models;

import com.intellij.localvcs.core.LocalVcsTestCase;
import com.intellij.localvcs.core.TestLocalVcs;
import com.intellij.localvcs.core.revisions.Revision;
import com.intellij.localvcs.integration.TestIdeaGateway;
import com.intellij.localvcs.integration.TestVirtualFile;
import com.intellij.mock.MockEditorFactory;
import com.intellij.mock.MockFileTypeManager;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileTypes.FileTypeManager;
import org.junit.Test;

import java.util.Date;
import java.util.List;

public class FileHistoryDialogModelTest extends LocalVcsTestCase {
  private TestLocalVcs vcs = new TestLocalVcs();
  private FileHistoryDialogModel m;

  @Test
  public void testIncludingUnsavedVersionInRevisions() {
    vcs.beginChangeSet();
    vcs.createFile("f", b("old"), -1);
    vcs.endChangeSet("1");

    initModelFor("f", "new");

    List<Revision> rr = m.getRevisions();

    assertEquals(2, rr.size());
    assertEquals("not saved", rr.get(0).getName());
    assertEquals("1", rr.get(1).getCauseAction());
  }

  @Test
  public void testUnsavedVersionTimestampMemorizedTheModelCreationTime() {
    setCurrentTimestamp(123);
    vcs.createFile("f", b("old"), -1);

    setCurrentTimestamp(456);
    initModelFor("f", "new");

    assertEquals(456L, m.getRevisions().get(0).getTimestamp());

    setCurrentTimestamp(789);
    assertEquals(456L, m.getRevisions().get(0).getTimestamp());
  }

  @Test
  public void testDoesNotIncludeUnsavedVersionDifferentOnlyInLineSeparator() {
    vcs.createFile("f", b("one\r\ntwo\r\n"), -1);

    initModelFor("f", "one\ntwo\n");

    assertEquals(1, m.getRevisions().size());
  }

  @Test
  public void testRevisionsListAfterPurgeContainsCurrentVersion() {
    setCurrentTimestamp(10);
    vcs.createFile("f", b(""), -1);
    vcs.purgeUpTo(20);

    initModelFor("f");

    setCurrentTimestamp(20);
    List<Revision> rr = m.getRevisions();
    setCurrentTimestamp(30);

    assertEquals(1, rr.size());
    assertEquals(20L, rr.get(0).getTimestamp());
  }

  @Test
  public void testDifferenceModelTitles() {
    vcs.createFile("old", b(""), 123L);
    vcs.rename("old", "new");

    initModelFor("new");
    m.selectRevisions(0, 1);

    FileDifferenceModel dm = m.getDifferenceModel();
    assertTrue(dm.getLeftTitle().endsWith(" - old"));
    assertTrue(dm.getRightTitle().endsWith(" - new"));
  }

  @Test
  public void testDifferenceModelContents() {
    vcs.createFile("f", b("old"), -1);
    vcs.changeFileContent("f", b("new"), -1);

    initModelFor("f");
    m.selectRevisions(0, 1);

    assertDifferenceModelContents("old", "new");
  }

  @Test
  public void testContentsWhenOnlyOneRevisionSelected() {
    vcs.createFile("f", b("old"), -1);
    vcs.changeFileContent("f", b("new"), -1);

    initModelFor("f");
    m.selectRevisions(1, 1);

    assertDifferenceModelContents("old", "new");
  }

  @Test
  public void testContentsForUnsavedVersion() {
    vcs.createFile("f", b("old"), -1);

    initModelFor("f", "unsaved");
    m.selectRevisions(0, 1);

    assertDifferenceModelContents("old", "unsaved");
  }

  @Test
  public void testTitlesForUnsavedEntry() {
    vcs.createDirectory("dir");
    vcs.createFile("dir/f", b("old"), -1);

    setCurrentTimestamp(new Date(2003, 01, 01).getTime());
    initModelFor("dir/f", "unsaved");
    m.selectRevisions(0, 1);

    FileDifferenceModel dm = m.getDifferenceModel();
    assertEquals("dir/f", dm.getTitle());
    assertEquals("01.02.03 0:00 - f", dm.getRightTitle());
  }

  private void assertDifferenceModelContents(String left, String right) {
    FileDifferenceModel dm = m.getDifferenceModel();

    FileTypeManager tm = new MockFileTypeManager();
    EditorFactory ef = new MockEditorFactory();

    assertEquals(left, dm.getLeftDiffContent(tm, ef).getText());
    assertEquals(right, dm.getRightDiffContent(tm, ef).getText());
  }

  private void initModelFor(String path) {
    initModelFor(path, new String(vcs.getEntry(path).getContent().getBytes()));
  }

  private void initModelFor(String path, String content) {
    TestVirtualFile f = new TestVirtualFile(path, content, -1);
    m = new FileHistoryDialogModel(f, vcs, new TestIdeaGateway());
  }
}
