package com.intellij.localvcs.integration.ui;

import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.TestStorage;
import com.intellij.localvcs.TestCase;
import com.intellij.localvcs.integration.TestVirtualFile;
import com.intellij.mock.MockDocument;
import com.intellij.mock.MockFileDocumentManagerImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

import java.util.List;

public class FileHistoryDialogModelTest extends TestCase {
  private LocalVcs vcs = new LocalVcs(new TestStorage());
  private MyFileDocumentManager dm = new MyFileDocumentManager();
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
    List<String> l = m.getLabels();

    assertEquals(2, l.size());
    assertEquals("2", l.get(0));
    assertEquals("1", l.get(1));
  }

  @Test
  public void testIncludingCurrentUnsavedVersionInLabels() {
    vcs.createFile("f", b("old"), null);
    vcs.apply();
    vcs.putLabel("1");

    dm.setCurrentContent("new");
    initModelFor("f");

    List<String> l = m.getLabels();

    assertEquals(2, l.size());
    assertEquals("current", l.get(0));
    assertEquals("1", l.get(1));
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
  public void testContentForCurrentUnsavedSavedVersion() {
    vcs.createFile("f", b("old"), null);
    vcs.apply();

    dm.setCurrentContent("new");
    m = new FileHistoryDialogModel(f("f"), vcs, dm);

    m.selectLabels(0, 1);

    assertEquals(c("old"), m.getLeftContent());
    assertEquals(c("new"), m.getRightContent());
  }

  private void initModelFor(String path) {
    TestVirtualFile f = new TestVirtualFile(path, null, null);
    m = new FileHistoryDialogModel(f, vcs, dm);
  }

  private TestVirtualFile f(String name) {
    return new TestVirtualFile(name, null, null);
  }

  private class MyFileDocumentManager extends MockFileDocumentManagerImpl {
    private String myContent;

    @Override
    public Document getDocument(final VirtualFile f) {
      return new MockDocument() {
        @Override
        public String getText() {
          return myContent == null ? getContent(f) : myContent;
        }
      };
    }

    private String getContent(VirtualFile f) {
      // todo review conversion
      return new String(vcs.getEntry(f.getPath()).getContent().getData());
    }

    private void setCurrentContent(String s) {
      myContent = s;
    }
  }
}
