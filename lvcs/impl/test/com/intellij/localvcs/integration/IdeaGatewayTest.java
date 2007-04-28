package com.intellij.localvcs.integration;

import com.intellij.localvcs.core.LocalVcs;
import com.intellij.localvcs.core.LocalVcsTestCase;
import com.intellij.localvcs.core.TestLocalVcs;
import com.intellij.localvcs.core.revisions.Revision;
import org.junit.Test;

import java.util.List;

public class IdeaGatewayTest extends LocalVcsTestCase {
  LocalVcs vcs = new TestLocalVcs();
  TestIdeaGateway gw = new TestIdeaGateway();

  @Test
  public void testRegisteringUnsavedDocuments() throws Exception {
    vcs.createFile("f1", null, -1);
    vcs.createFile("f2", null, -1);

    gw.addUnsavedDocument("f1", "one");
    gw.addUnsavedDocument("f2", "two");

    assertEquals(1, vcs.getRevisionsFor("f1").size());
    assertEquals(1, vcs.getRevisionsFor("f2").size());

    Clock.setCurrentTimestamp(123);
    gw.registerUnsavedDocuments(vcs);

    List<Revision> rr1 = vcs.getRevisionsFor("f1");
    List<Revision> rr2 = vcs.getRevisionsFor("f2");

    assertEquals(2, rr1.size());
    assertEquals(c("one"), rr1.get(0).getEntry().getContent());
    assertEquals(123, rr1.get(0).getEntry().getTimestamp());

    assertEquals(2, rr2.size());
    assertEquals(c("two"), rr2.get(0).getEntry().getContent());
    assertEquals(123, rr2.get(0).getEntry().getTimestamp());
  }

  @Test
  public void testFilteringNotAllowedDocuments() {
    vcs.createFile("f", null, -1);
    gw.addUnsavedDocument("f", "content");

    ((TestFileFilter)gw.getFileFilter()).setNotAllowedFiles(gw.getUnsavedDocumentFiles());

    assertEquals(1, vcs.getRevisionsFor("f").size());

    gw.registerUnsavedDocuments(vcs);
    assertEquals(1, vcs.getRevisionsFor("f").size());
  }

  @Test
  public void testFilteringDocumentsNotUnderContentRoot() {
    vcs.createFile("f", null, -1);
    gw.addUnsavedDocument("f", "content");

    ((TestFileFilter)gw.getFileFilter()).setFilesNotUnderContentRoot(gw.getUnsavedDocumentFiles());

    assertEquals(1, vcs.getRevisionsFor("f").size());

    gw.registerUnsavedDocuments(vcs);
    assertEquals(1, vcs.getRevisionsFor("f").size());
  }
}
