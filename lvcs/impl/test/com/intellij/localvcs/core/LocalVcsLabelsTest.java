package com.intellij.localvcs.core;

import com.intellij.localvcs.core.revisions.Revision;
import org.junit.Test;

import java.util.List;

public class LocalVcsLabelsTest extends LocalVcsTestCase {
  LocalVcs vcs = new TestLocalVcs();

  @Test
  public void testLabels() {
    vcs.createFile("file", null, -1);
    vcs.putLabel("file", "1");
    vcs.changeFileContent("file", null, -1);
    vcs.putLabel("file", "2");

    List<Revision> rr = vcs.getRevisionsFor("file");
    assertEquals(4, rr.size());

    assertEquals("2", rr.get(0).getName());
    assertNull(rr.get(1).getName());
    assertEquals("1", rr.get(2).getName());
    assertNull(rr.get(3).getName());
  }

  @Test
  public void testDoesNotIncludeLabelsForAnotherEntry() {
    vcs.createFile("one", null, -1);
    vcs.createFile("two", null, -1);
    vcs.putLabel("one", "one");
    vcs.putLabel("two", "two");

    List<Revision> rr = vcs.getRevisionsFor("one");
    assertEquals(2, rr.size());
    assertEquals("one", rr.get(0).getName());

    rr = vcs.getRevisionsFor("two");
    assertEquals(2, rr.size());
    assertEquals("two", rr.get(0).getName());
  }

  @Test
  public void testEntryLabelTimestamps() {
    setCurrentTimestamp(10);
    vcs.createFile("file", null, -1);
    setCurrentTimestamp(20);
    vcs.putLabel("file", "1");
    setCurrentTimestamp(30);
    vcs.putLabel("file", "1");

    List<Revision> rr = vcs.getRevisionsFor("file");
    assertEquals(30, rr.get(0).getTimestamp());
    assertEquals(20, rr.get(1).getTimestamp());
    assertEquals(10, rr.get(2).getTimestamp());
  }

  @Test
  public void testContent() {
    vcs.createFile("file", cf("old"), -1);
    vcs.putLabel("file", "");
    vcs.changeFileContent("file", cf("new"), -1);
    vcs.putLabel("file", "");

    List<Revision> rr = vcs.getRevisionsFor("file");

    assertEquals(c("new"), rr.get(0).getEntry().getContent());
    assertEquals(c("old"), rr.get(2).getEntry().getContent());
  }

  @Test
  public void testLabelsAfterPurge() {
    setCurrentTimestamp(10);
    vcs.createFile("file", null, -1);
    setCurrentTimestamp(20);
    vcs.putLabel("file", "l");

    vcs.purgeObsolete(5);

    List<Revision> rr = vcs.getRevisionsFor("file");
    assertEquals(1, rr.size());
    assertEquals("l", rr.get(0).getName());
  }

  @Test
  public void testGlobalLabels() {
    vcs.createFile("one", null, -1);
    vcs.putLabel("1");
    vcs.createFile("two", null, -1);
    vcs.putLabel("2");

    List<Revision> rr = vcs.getRevisionsFor("one");
    assertEquals(3, rr.size());
    assertEquals("2", rr.get(0).getName());
    assertEquals("1", rr.get(1).getName());

    rr = vcs.getRevisionsFor("two");
    assertEquals(2, rr.size());
    assertEquals("2", rr.get(0).getName());
  }

  @Test
  public void testGlobalLabelTimestamps() {
    setCurrentTimestamp(10);
    vcs.createFile("file", null, -1);
    setCurrentTimestamp(20);
    vcs.putLabel("");

    List<Revision> rr = vcs.getRevisionsFor("file");
    assertEquals(20, rr.get(0).getTimestamp());
    assertEquals(10, rr.get(1).getTimestamp());
  }

  @Test
  public void testLabelsDuringChangeSet() {
    vcs.createFile("f", null, -1);
    vcs.beginChangeSet();
    vcs.changeFileContent("f", null, -1);
    vcs.putLabel("label");
    vcs.endChangeSet("changeSet");

    List<Revision> rr = vcs.getRevisionsFor("f");
    assertEquals(2, rr.size());
    assertEquals("changeSet", rr.get(0).getCauseChangeName());
    assertEquals(null, rr.get(1).getCauseChangeName());
  }
}