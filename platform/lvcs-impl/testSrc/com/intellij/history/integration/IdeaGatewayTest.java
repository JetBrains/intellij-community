package com.intellij.history.integration;

import com.intellij.history.core.InMemoryLocalVcs;
import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.LocalVcsTestCase;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.Clock;
import org.junit.Test;

import java.util.List;

public class IdeaGatewayTest extends LocalVcsTestCase {
  LocalVcs vcs = new InMemoryLocalVcs();
  TestIdeaGateway gw = new TestIdeaGateway();

  @Test
  public void testRegisteringUnsavedDocuments() throws Exception {
    vcs.createDirectory("root");
    long timestamp = -1;
    vcs.createFile("root/f1", null, timestamp, false);
    long timestamp1 = -1;
    vcs.createFile("root/f2", null, timestamp1, false);

    gw.addUnsavedDocument("root/f1", "one");
    gw.addUnsavedDocument("root/f2", "two");

    assertEquals(3, vcs.getRevisionsFor("root").size());
    assertEquals(1, vcs.getRevisionsFor("root/f1").size());
    assertEquals(1, vcs.getRevisionsFor("root/f2").size());

    Clock.setCurrentTimestamp(123);
    gw.registerUnsavedDocuments(vcs);

    List<Revision> rr1 = vcs.getRevisionsFor("root/f1");
    List<Revision> rr2 = vcs.getRevisionsFor("root/f2");

    assertEquals(2, rr1.size());
    assertEquals(c("one"), rr1.get(0).getEntry().getContent());
    assertEquals(123, rr1.get(0).getEntry().getTimestamp());

    assertEquals(2, rr2.size());
    assertEquals(c("two"), rr2.get(0).getEntry().getContent());
    assertEquals(123, rr2.get(0).getEntry().getTimestamp());

    assertEquals(4, vcs.getRevisionsFor("root").size());
  }

  @Test
  public void testFilteringDocumentsWithoutFiles() {
    long timestamp = -1;
    vcs.createFile("f", cf("file"), timestamp, false);
    gw.addUnsavedDocumentWithoutFile("f", "doc");
    gw.registerUnsavedDocuments(vcs);

    assertEquals(c("file"), vcs.getEntry("f").getContent());
  }

  @Test
  public void testFilteringDocumentsForDeletedFiles() {
    long timestamp = -1;
    vcs.createFile("f", cf("file"), timestamp, false);
    gw.addUnsavedDocumentForDeletedFile("f", "doc");

    gw.registerUnsavedDocuments(vcs);
    assertEquals(c("file"), vcs.getEntry("f").getContent());
  }

  @Test
  public void testFilteringNotAllowedDocuments() {
    long timestamp = -1;
    vcs.createFile("f", null, timestamp, false);
    gw.addUnsavedDocument("f", "content");

    ((TestFileFilter)gw.getFileFilter()).setNotAllowedFiles(gw.getUnsavedDocumentFiles());

    assertEquals(1, vcs.getRevisionsFor("f").size());

    gw.registerUnsavedDocuments(vcs);
    assertEquals(1, vcs.getRevisionsFor("f").size());
  }

  @Test
  public void testFilteringDocumentsNotUnderContentRoot() {
    long timestamp = -1;
    vcs.createFile("f", null, timestamp, false);
    gw.addUnsavedDocument("f", "content");

    ((TestFileFilter)gw.getFileFilter()).setFilesNotUnderContentRoot(gw.getUnsavedDocumentFiles());

    assertEquals(1, vcs.getRevisionsFor("f").size());

    gw.registerUnsavedDocuments(vcs);
    assertEquals(1, vcs.getRevisionsFor("f").size());
  }
}
