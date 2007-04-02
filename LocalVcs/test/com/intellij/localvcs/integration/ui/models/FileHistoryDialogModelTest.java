package com.intellij.localvcs.integration.ui.models;

import com.intellij.localvcs.Label;
import com.intellij.localvcs.LocalVcsTestCase;
import com.intellij.localvcs.TestLocalVcs;
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
  public void testIncludingUnsavedVersionInLabels() {
    vcs.beginChangeSet();
    vcs.createFile("f", b("old"), -1);
    vcs.endChangeSet("1");

    initModelFor("f", "new");

    List<Label> ll = m.getLabels();

    assertEquals(2, ll.size());
    assertEquals("not saved", ll.get(0).getName());
    assertEquals("1", ll.get(1).getName());
  }

  @Test
  public void testUnsavedVersionTimestampMemorizedTheModelCreationTime() {
    setCurrentTimestamp(123);
    vcs.createFile("f", b("old"), -1);

    setCurrentTimestamp(456);
    initModelFor("f", "new");

    assertEquals(456L, m.getLabels().get(0).getTimestamp());

    setCurrentTimestamp(789);
    assertEquals(456L, m.getLabels().get(0).getTimestamp());
  }

  @Test
  public void testDoesNotIncludeUnsavedVersionDifferentOnlyInLineSeparator() {
    vcs.createFile("f", b("one\r\ntwo\r\n"), -1);

    initModelFor("f", "one\ntwo\n");

    assertEquals(1, m.getLabels().size());
  }

  @Test
  public void testLabelsListAfterPurgeContainsCurrentVersion() {
    setCurrentTimestamp(10);
    vcs.createFile("f", b(""), -1);
    vcs.purgeUpTo(20);

    initModelFor("f");

    setCurrentTimestamp(20);
    List<Label> ll = m.getLabels();
    setCurrentTimestamp(30);

    assertEquals(1, ll.size());
    assertEquals(20L, ll.get(0).getTimestamp());
  }

  @Test
  public void testDifferenceModelTitles() {
    vcs.createFile("old", b(""), 123L);
    vcs.rename("old", "new");

    initModelFor("new");
    m.selectLabels(0, 1);

    FileDifferenceModel dm = m.getDifferenceModel();
    assertTrue(dm.getLeftTitle().endsWith(" - old"));
    assertTrue(dm.getRightTitle().endsWith(" - new"));
  }

  @Test
  public void testDifferenceModelContents() {
    vcs.createFile("f", b("old"), -1);
    vcs.changeFileContent("f", b("new"), -1);

    initModelFor("f");
    m.selectLabels(0, 1);

    assertDifferenceModelContents("old", "new");
  }

  @Test
  public void testContentsWhenOnlyOneLabelSelected() {
    vcs.createFile("f", b("old"), -1);
    vcs.changeFileContent("f", b("new"), -1);

    initModelFor("f");
    m.selectLabels(1, 1);

    assertDifferenceModelContents("old", "new");
  }

  @Test
  public void testContentsForUnsavedVersion() {
    vcs.createFile("f", b("old"), -1);

    initModelFor("f", "unsaved");
    m.selectLabels(0, 1);

    assertDifferenceModelContents("old", "unsaved");
  }

  @Test
  public void testTitlesForUnsavedEntry() {
    vcs.createDirectory("dir");
    vcs.createFile("dir/f", b("old"), -1);

    setCurrentTimestamp(new Date(2003, 01, 01).getTime());
    initModelFor("dir/f", "unsaved");
    m.selectLabels(0, 1);

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
