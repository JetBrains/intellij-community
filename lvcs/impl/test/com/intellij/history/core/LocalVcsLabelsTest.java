package com.intellij.history.core;

import com.intellij.history.core.changes.PutLabelChange;
import com.intellij.history.core.revisions.Revision;
import org.junit.Test;

import java.util.List;

public class LocalVcsLabelsTest extends LocalVcsTestCase {
  LocalVcs vcs = new InMemoryLocalVcs();

  @Test
  public void testUserLabels() {
    vcs.createFile("file", null, -1);
    vcs.putUserLabel("file", "1");
    vcs.changeFileContent("file", null, -1);
    vcs.putUserLabel("file", "2");

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
    vcs.putUserLabel("one", "one");
    vcs.putUserLabel("two", "two");

    List<Revision> rr = vcs.getRevisionsFor("one");
    assertEquals(2, rr.size());
    assertEquals("one", rr.get(0).getName());

    rr = vcs.getRevisionsFor("two");
    assertEquals(2, rr.size());
    assertEquals("two", rr.get(0).getName());
  }

  @Test
  public void testLabelTimestamps() {
    setCurrentTimestamp(10);
    vcs.createFile("file", null, -1);
    setCurrentTimestamp(20);
    vcs.putUserLabel("file", "1");
    setCurrentTimestamp(30);
    vcs.putUserLabel("file", "1");

    List<Revision> rr = vcs.getRevisionsFor("file");
    assertEquals(30, rr.get(0).getTimestamp());
    assertEquals(20, rr.get(1).getTimestamp());
    assertEquals(10, rr.get(2).getTimestamp());
  }

  @Test
  public void testContent() {
    vcs.createFile("file", cf("old"), -1);
    vcs.putUserLabel("file", "");
    vcs.changeFileContent("file", cf("new"), -1);
    vcs.putUserLabel("file", "");

    List<Revision> rr = vcs.getRevisionsFor("file");

    assertEquals(c("new"), rr.get(0).getEntry().getContent());
    assertEquals(c("old"), rr.get(2).getEntry().getContent());
  }

  @Test
  public void testLabelsAfterPurge() {
    setCurrentTimestamp(10);
    vcs.createFile("file", null, -1);
    setCurrentTimestamp(20);
    vcs.putUserLabel("file", "l");

    vcs.purgeObsolete(5);

    List<Revision> rr = vcs.getRevisionsFor("file");
    assertEquals(1, rr.size());
    assertEquals("l", rr.get(0).getName());
  }

  @Test
  public void testGlobalUserLabels() {
    vcs.createFile("one", null, -1);
    vcs.putUserLabel("1");
    vcs.createFile("two", null, -1);
    vcs.putUserLabel("2");

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
    vcs.putUserLabel("");

    List<Revision> rr = vcs.getRevisionsFor("file");
    assertEquals(20, rr.get(0).getTimestamp());
    assertEquals(10, rr.get(1).getTimestamp());
  }

  @Test
  public void testLabelsDuringChangeSet() {
    vcs.createFile("f", null, -1);
    vcs.beginChangeSet();
    vcs.changeFileContent("f", null, -1);
    vcs.putUserLabel("label");
    vcs.endChangeSet("changeSet");

    List<Revision> rr = vcs.getRevisionsFor("f");
    assertEquals(2, rr.size());
    assertEquals("changeSet", rr.get(0).getCauseChangeName());
    assertEquals(null, rr.get(1).getCauseChangeName());
  }

  @Test
  public void testSystemLabels() {
    vcs.createFile("f1", null, -1);
    vcs.createFile("f2", null, -1);

    setCurrentTimestamp(123);
    vcs.putSystemLabel("label");

    List<Revision> rr1 = vcs.getRevisionsFor("f1");
    List<Revision> rr2 = vcs.getRevisionsFor("f2");
    assertEquals(2, rr1.size());
    assertEquals(2, rr2.size());

    assertEquals("label", rr1.get(0).getName());
    assertEquals("label", rr2.get(0).getName());

    PutLabelChange l = (PutLabelChange)rr1.get(0).getCauseChange();
    assertTrue(l.isSystemLabel());
    assertEquals(123, l.getTimestamp());
  }
}