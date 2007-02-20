package com.intellij.localvcs.integration.ui;

import com.intellij.localvcs.LocalVcsTestCase;
import com.intellij.localvcs.TestLocalVcs;
import com.intellij.localvcs.integration.TestIdeaGateway;
import com.intellij.localvcs.integration.TestVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import static org.easymock.classextension.EasyMock.*;
import org.junit.Test;

import java.util.List;

public class FileHistoryDialogModelTest extends LocalVcsTestCase {
  private TestLocalVcs vcs = new TestLocalVcs();
  private FileHistoryDialogModel m;

  @Test
  public void testLabelsList() {
    vcs.createFile("f", b(""), null);
    vcs.apply();
    vcs.putLabel("1");

    vcs.changeFileContent("f", b(""), null);
    vcs.apply();
    vcs.putLabel("2");

    initModelFor("f");
    List<String> ll = m.getLabels();

    assertEquals(2, ll.size());
    assertEquals("2", ll.get(0));
    assertEquals("1", ll.get(1));
  }

  @Test
  public void testIncludingUnsavedVersionInLabels() {
    vcs.createFile("f", b("old"), null);
    vcs.apply();
    vcs.putLabel("1");

    initModelFor("f", "new");

    List<String> ll = m.getLabels();

    assertEquals(2, ll.size());
    assertEquals("not saved", ll.get(0));
    assertEquals("1", ll.get(1));
  }

  @Test
  public void testDoesNotIncludeUnsavedVersionDifferentOnlyInLineSeparator() {
    vcs.createFile("f", b("one\r\ntwo\r\n"), null);
    vcs.apply();

    initModelFor("f", "one\ntwo\n");

    assertEquals(1, m.getLabels().size());
  }

  @Test
  public void testLabelsListAfterPurgeConteinsOnlyCurrentVersion() {
    vcs.setCurrentTimestamp(10);
    vcs.createFile("f", b(""), null);
    vcs.apply();
    vcs.purgeUpTo(20);

    initModelFor("f");

    List<String> ll = m.getLabels();

    assertEquals(1, ll.size());
    assertEquals("current", ll.get(0));
  }

  @Test
  public void testContentForLabels() {
    vcs.createFile("f", b("old"), null);
    vcs.apply();
    vcs.changeFileContent("f", b("new"), null);
    vcs.apply();

    initModelFor("f");
    m.selectLabels(0, 1);

    assertEquals(c("old"), m.getLeftContent());
    assertEquals(c("new"), m.getRightContent());
  }

  @Test
  public void testContentWhenOnlyOneLabelSelected() {
    vcs.createFile("f", b("old"), null);
    vcs.apply();
    vcs.changeFileContent("f", b("new"), null);
    vcs.apply();

    initModelFor("f");
    m.selectLabels(1, 1);

    assertEquals(c("old"), m.getLeftContent());
    assertEquals(c("new"), m.getRightContent());
  }

  @Test
  public void testContentForUnsavedVersion() {
    vcs.createFile("f", b("old"), null);
    vcs.apply();

    initModelFor("f", "new");

    m.selectLabels(0, 1);

    assertEquals(c("old"), m.getLeftContent());
    assertEquals(c("new"), m.getRightContent());
  }

  @Test
  public void testRevertion() throws Exception {
    vcs.createFile("file", b("old"), 1L);
    vcs.apply();
    vcs.changeFileContent("file", b("new"), null);
    vcs.apply();

    VirtualFile f = createMock(VirtualFile.class);
    expect(f.contentsToByteArray()).andReturn(b("new"));
    expect(f.getPath()).andStubReturn("file");
    expect(f.getName()).andStubReturn("file");
    expect(f.getTimeStamp()).andStubReturn(2L);
    f.setBinaryContent(aryEq(b("old")), eq(-1L), eq(1L));
    replay(f);

    m = new FileHistoryDialogModel(f, vcs, new TestIdeaGateway());
    m.selectLabels(1, 1);

    m.revert();
    verify(f);
  }

  private void initModelFor(String path) {
    initModelFor(path, new String(vcs.getEntry(path).getContent().getBytes()));
  }

  private void initModelFor(String path, String content) {
    TestVirtualFile f = new TestVirtualFile(path, content, null);
    m = new FileHistoryDialogModel(f, vcs, new TestIdeaGateway());
  }
}
