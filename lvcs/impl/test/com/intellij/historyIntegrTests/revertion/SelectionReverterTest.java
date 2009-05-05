package com.intellij.historyIntegrTests.revertion;

import com.intellij.history.Clock;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.revertion.SelectionReverter;
import com.intellij.history.integration.ui.models.SelectionCalculator;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.historyIntegrTests.IntegrationTestCase;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.List;
import java.util.Date;
import java.util.ArrayList;

public class SelectionReverterTest extends IntegrationTestCase {
  private VirtualFile f;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    f = root.createChildData(null, "f.txt");
  }

  public void testBasics() throws IOException {
    String before = "public class Bar {\n" +
                    "  public String foo() {\n" +
                    "    return \"old\";\n" +
                    "  }\n" +
                    "}\n";
    String after = "public class NewBar {\n" +
                   "  public String foo() {\n" +
                   "    return \"new\";\n" +
                   "  }\n" +
                   "  public abstract bar();\n" +
                   "}\n";

    f.setBinaryContent(before.getBytes());
    f.setBinaryContent(after.getBytes());

    revertToPreviousRevision(2, 2);
    
    String expected = "public class NewBar {\n" +
                      "  public String foo() {\n" +
                      "    return \"old\";\n" +
                      "  }\n" +
                      "  public abstract bar();\n" +
                      "}\n";
    assertEquals(expected, new String(f.contentsToByteArray()));
  }

  public void testChangeSetName() throws IOException {
    Clock.setCurrentTimestamp(new Date(2001, 1, 11, 12, 30).getTime());

    f.setBinaryContent("one".getBytes());
    f.setBinaryContent("two".getBytes());

    revertToPreviousRevision(0, 0);

    List<Revision> rr = getVcsRevisionsFor(f);
    assertEquals(4, rr.size());
    assertEquals("Revert of selection to 11.02.01 12:30", rr.get(0).getCauseChangeName());
  }

  public void testAskingForReadOnlyStatusClearingOnlyForTheSpecifiedFile() throws Exception {
    root.createChildData(null, "foo1.txt");
    f.setBinaryContent("one".getBytes());
    root.createChildData(null, "foo2.txt");
    f.setBinaryContent("two".getBytes());
    root.createChildData(null, "foo3.txt");

    final List<VirtualFile> files = new ArrayList<VirtualFile>();
    gateway = new IdeaGateway(myProject) {
      @Override
      public boolean ensureFilesAreWritable(List<VirtualFile> ff) {
        files.addAll(ff);
        return true;
      }
    };

    List<String> errors = checkCanRevertToPreviousRevision();
    assertTrue(errors.isEmpty());

    assertEquals(1, files.size());
    assertEquals(f, files.get(0));
  }

  private void revertToPreviousRevision(int from, int to) throws IOException {
    createReverter(from, to).revert();
  }

  private List<String> checkCanRevertToPreviousRevision() throws IOException {
    return createReverter(0, 0).checkCanRevert();
  }

  private SelectionReverter createReverter(int from, int to) {
    List<Revision> rr = getVcsRevisionsFor(f);
    SelectionCalculator c = new SelectionCalculator(gateway, rr, from, to);
    Revision leftRev = rr.get(1);
    Entry right = getVcsEntry(f);

    return new SelectionReverter(getVcs(), gateway, c, leftRev, right, from, to);
  }
}
