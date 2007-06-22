package com.intellij.history.core;

import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

public class LocalVcsChangeSetsTest extends LocalVcsTestCase {
  LocalVcs vcs = new InMemoryLocalVcs();

  @Test
  public void testApplyingChangesRightAfterChange() {
    vcs.createFile("file", cf("content"), -1);
    assertEquals(c("content"), vcs.getEntry("file").getContent());

    vcs.changeFileContent("file", cf("new content"), -1);
    assertEquals(c("new content"), vcs.getEntry("file").getContent());
  }

  @Test
  @Ignore("unignore when service states will be completed")
  public void testStartingChangeSetTwiceThrowsException() {
    vcs.beginChangeSet();
    try {
      vcs.beginChangeSet();
      fail();
    }
    catch (IllegalStateException e) {
    }
  }

  @Test
  @Ignore("unignore when service states will be completed")
  public void testFinishingChangeSetWithoutStartingItThrowsException() {
    try {
      vcs.endChangeSet(null);
      fail();
    }
    catch (IllegalStateException e) {
    }
  }

  @Test
  public void testAskingForNewFileContentDuringChangeSet() {
    vcs.beginChangeSet();
    vcs.createFile("file", cf("content"), -1);

    Entry e = vcs.findEntry("file");

    assertNotNull(e);
    assertEquals(c("content"), e.getContent());
  }

  @Test
  public void testTreatingSeveralChangesDuringChangeSetAsOne() {
    vcs.beginChangeSet();
    vcs.createDirectory("dir");
    vcs.createFile("dir/one", null, -1);
    vcs.createFile("dir/two", null, -1);
    vcs.endChangeSet(null);

    assertEquals(1, vcs.getRevisionsFor("dir").size());
  }

  @Test
  public void testTreatingSeveralChangesOutsideOfChangeSetAsSeparate() {
    vcs.createDirectory("dir");
    vcs.createFile("dir/one", null, -1);
    vcs.createFile("dir/two", null, -1);

    vcs.beginChangeSet();
    vcs.endChangeSet(null);

    vcs.createFile("dir/three", null, -1);
    vcs.createFile("dir/four", null, -1);

    assertEquals(5, vcs.getRevisionsFor("dir").size());
  }

  @Test
  public void testIgnoringInnerChangeSets() {
    vcs.beginChangeSet();
    vcs.createDirectory("dir");
    vcs.beginChangeSet();
    vcs.createFile("dir/one", null, -1);
    vcs.endChangeSet("inner");
    vcs.createFile("dir/two", null, -1);
    vcs.endChangeSet("outer");

    List<Revision> rr = vcs.getRevisionsFor("dir");
    assertEquals(1, rr.size());
    assertEquals("outer", rr.get(0).getCauseChangeName());
  }

  @Test
  public void testIgnoringEmptyChangeSets() {
    vcs.beginChangeSet();
    vcs.createDirectory("dir");
    vcs.endChangeSet(null);

    assertEquals(1, vcs.getChangeList().getChanges().size());

    vcs.beginChangeSet();
    vcs.endChangeSet(null);

    assertEquals(1, vcs.getChangeList().getChanges().size());
  }
}