package com.intellij.historyIntegrTests.revertion;

import com.intellij.history.Clock;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.revertion.SelectionReverter;
import com.intellij.history.integration.ui.models.SelectionCalculator;
import com.intellij.historyIntegrTests.IntegrationTestCase;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.List;
import java.util.Date;

public class SelectionReverterTest extends IntegrationTestCase {
  private VirtualFile f;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    f = root.createChildData(null, "f.java");
  }

  public void testFoo() throws IOException {
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

  private void revertToPreviousRevision(int from, int to) throws IOException {
    List<Revision> rr = getVcsRevisionsFor(f);
    SelectionCalculator c = new SelectionCalculator(rr, from, to);
    Revision leftRev = rr.get(1);
    Entry right = getVcsEntry(f);

    SelectionReverter r = new SelectionReverter(getVcs(), gateway, c, leftRev, right, from, to);
    r.revert();
  }
}
