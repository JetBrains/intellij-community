package com.intellij.localvcs.core;

import com.intellij.localvcs.core.revisions.Difference;
import static com.intellij.localvcs.core.revisions.Difference.Kind.*;
import com.intellij.localvcs.core.revisions.Revision;
import com.intellij.localvcs.core.tree.Entry;
import org.junit.Test;

import java.util.List;

public class LocalVcsRevisionsTest extends LocalVcsTestCase {
  LocalVcs vcs = new TestLocalVcs();

  @Test
  public void testRevisions() {
    vcs.createFile("file", cf("old"), -1);
    vcs.changeFileContent("file", cf("new"), -1);

    List<Revision> rr = vcs.getRevisionsFor("file");
    assertEquals(2, rr.size());
    assertEquals(c("new"), rr.get(0).getEntry().getContent());
    assertEquals(c("old"), rr.get(1).getEntry().getContent());
  }

  @Test
  public void testNamedAndUnnamedCauseActions() {
    vcs.beginChangeSet();
    vcs.createFile("file", null, -1);
    vcs.endChangeSet("name");

    vcs.changeFileContent("file", null, -1);

    List<Revision> rr = vcs.getRevisionsFor("file");
    assertEquals(2, rr.size());

    assertNull(rr.get(0).getCauseAction());
    assertEquals("name", rr.get(1).getCauseAction());
  }

  @Test
  public void testIncludingCurrentVersionIntoRevisionsAfterPurge() {
    setCurrentTimestamp(10);
    vcs.createFile("file", cf("content"), -1);
    vcs.purgeObsolete(0);

    setCurrentTimestamp(30);

    List<Revision> rr = vcs.getRevisionsFor("file");
    assertEquals(1, rr.size());

    Revision r = rr.get(0);
    assertNull(r.getName());
    assertNull(r.getCauseAction());
    assertEquals(30, r.getTimestamp());

    Entry e = r.getEntry();
    assertEquals("file", e.getName());
    assertEquals(c("content"), e.getContent());
  }

  @Test
  public void testIncludingVersionBeforeFirstChangeAfterPurge() {
    setCurrentTimestamp(10);
    vcs.createFile("file", cf("one"), -1);
    setCurrentTimestamp(20);
    vcs.changeFileContent("file", cf("two"), -1);

    vcs.purgeObsolete(5);

    List<Revision> rr = vcs.getRevisionsFor("file");
    assertEquals(2, rr.size());

    assertEquals(c("two"), rr.get(0).getEntry().getContent());
    assertEquals(c("one"), rr.get(1).getEntry().getContent());
  }

  @Test
  public void testDoesNotIncludeRevisionsForAnotherEntries() {
    vcs.beginChangeSet();
    vcs.createFile("file1", null, -1);
    vcs.endChangeSet("1");

    vcs.beginChangeSet();
    vcs.createFile("file2", null, -1);
    vcs.endChangeSet("2");

    List<Revision> rr = vcs.getRevisionsFor("file2");
    assertEquals(1, rr.size());
    assertEquals("2", rr.get(0).getCauseAction());
  }

  @Test
  public void testRevisionsTimestamp() {
    setCurrentTimestamp(10);
    vcs.createFile("file", null, -1);

    setCurrentTimestamp(20);
    vcs.changeFileContent("file", null, -1);

    setCurrentTimestamp(30);
    vcs.changeFileContent("file", null, -1);

    List<Revision> rr = vcs.getRevisionsFor("file");
    assertEquals(30L, rr.get(0).getTimestamp());
    assertEquals(20L, rr.get(1).getTimestamp());
    assertEquals(10L, rr.get(2).getTimestamp());
  }

  @Test
  public void testTimestampForCurrentRevisionAfterPurgeFromCurrentTimestamp() {
    setCurrentTimestamp(10);
    vcs.createFile("file", null, -1);
    vcs.purgeObsolete(0);

    setCurrentTimestamp(20);
    assertEquals(20L, vcs.getRevisionsFor("file").get(0).getTimestamp());
  }

  @Test
  public void testTimestampForLastRevisionAfterPurge() {
    setCurrentTimestamp(10);
    vcs.createFile("file", cf(""), -1);

    setCurrentTimestamp(20);
    vcs.changeFileContent("file", cf(""), -1);

    setCurrentTimestamp(30);
    vcs.changeFileContent("file", cf(""), -1);

    vcs.purgeObsolete(15);

    List<Revision> rr = vcs.getRevisionsFor("file");
    assertEquals(30L, rr.get(0).getTimestamp());
    assertEquals(20L, rr.get(1).getTimestamp());
    assertEquals(20L, rr.get(2).getTimestamp());
  }

  @Test
  public void testRevisionsForFileCreatedWithSameNameAsDeletedOne() {
    vcs.createFile("file", cf("old"), -1);
    vcs.delete("file");
    vcs.createFile("file", cf("new"), -1);

    List<Revision> rr = vcs.getRevisionsFor("file");
    assertEquals(1, rr.size());

    Entry e = rr.get(0).getEntry();
    assertEquals("file", e.getName());
    assertEquals(c("new"), e.getContent());
  }

  @Test
  public void testRevisionsForFileCreatenInPlaceOfRenamedOne() {
    vcs.createFile("file1", cf("content1"), -1);
    vcs.rename("file1", "file2");
    vcs.createFile("file1", cf("content2"), -1);

    List<Revision> rr = vcs.getRevisionsFor("file1");
    assertEquals(1, rr.size());

    Entry e = rr.get(0).getEntry();
    assertEquals("file1", e.getName());
    assertEquals(c("content2"), e.getContent());

    rr = vcs.getRevisionsFor("file2");
    assertEquals(2, rr.size());

    e = rr.get(0).getEntry();
    assertEquals("file2", e.getName());
    assertEquals(c("content1"), e.getContent());

    e = rr.get(1).getEntry();
    assertEquals("file1", e.getName());
    assertEquals(c("content1"), e.getContent());
  }

  @Test
  public void testGettingEntryFromRevision() {
    vcs.createFile("file", cf("content"), 123L);
    vcs.changeFileContent("file", cf("new content"), 456L);

    List<Revision> rr = vcs.getRevisionsFor("file");

    Entry e = rr.get(0).getEntry();
    assertEquals("file", e.getName());
    assertEquals(c("new content"), e.getContent());
    assertEquals(456L, e.getTimestamp());

    e = rr.get(1).getEntry();
    assertEquals("file", e.getName());
    assertEquals(c("content"), e.getContent());
    assertEquals(123L, e.getTimestamp());
  }

  @Test
  public void testGettingEntryFromRevisionInRenamedDir() {
    vcs.createDirectory("dir");
    vcs.createFile("dir/file", null, -1);
    vcs.rename("dir", "newDir");
    vcs.changeFileContent("newDir/file", null, -1);

    List<Revision> rr = vcs.getRevisionsFor("newDir/file");
    assertEquals(3, rr.size());

    assertEquals("newDir/file", rr.get(0).getEntry().getPath());
    assertEquals("newDir/file", rr.get(1).getEntry().getPath());
    assertEquals("dir/file", rr.get(2).getEntry().getPath());
  }

  @Test
  public void testGettingEntryFromRevisionDoesNotChangeRootEntry() {
    vcs.createFile("file", cf("content"), -1);
    vcs.changeFileContent("file", cf("new content"), -1);

    List<Revision> rr = vcs.getRevisionsFor("file");

    assertEquals(c("content"), rr.get(1).getEntry().getContent());
    assertEquals(c("new content"), vcs.getEntry("file").getContent());
  }

  @Test
  public void testGettingDifferenceBetweenRevisionls() {
    vcs.createFile("file", cf("content"), -1);
    vcs.changeFileContent("file", cf("new content"), -1);

    List<Revision> rr = vcs.getRevisionsFor("file");

    Revision recent = rr.get(0);
    Revision prev = rr.get(1);

    Difference d = prev.getDifferenceWith(recent);
    assertEquals(MODIFIED, d.getKind());
    assertEquals(c("content"), d.getLeft().getContent());
    assertEquals(c("new content"), d.getRight().getContent());
  }

  @Test
  public void testNoDifferenceBetweenRevisions() {
    vcs.createFile("file", cf("content"), -1);

    List<Revision> rr = vcs.getRevisionsFor("file");

    Revision one = rr.get(0);
    Revision two = rr.get(0);

    Difference d = one.getDifferenceWith(two);
    assertFalse(d.hasDifference());
  }

  @Test
  public void testDifferenceForDirectory() {
    vcs.createDirectory("dir");
    vcs.createFile("dir/file", null, -1);

    List<Revision> rr = vcs.getRevisionsFor("dir");
    assertEquals(2, rr.size());

    Revision recent = rr.get(0);
    Revision prev = rr.get(1);

    Difference d = prev.getDifferenceWith(recent);
    assertEquals(NOT_MODIFIED, d.getKind());
    assertEquals(1, d.getChildren().size());

    d = d.getChildren().get(0);
    assertEquals(CREATED, d.getKind());
    assertNull(d.getLeft());
    assertEquals("file", d.getRight().getName());
  }

  @Test
  public void testNoDifferenceForDirectoryWithEqualContents() {
    vcs.createDirectory("dir");
    vcs.createFile("dir/file", null, -1);
    vcs.delete("dir/file");

    List<Revision> rr = vcs.getRevisionsFor("dir");

    Difference d = rr.get(0).getDifferenceWith(rr.get(2));
    assertFalse(d.hasDifference());
  }

  @Test
  public void testDoesNotIncludeNotModifiedDifferences() {
    vcs.beginChangeSet();
    vcs.createDirectory("dir1");
    vcs.createDirectory("dir1/dir2");
    vcs.createDirectory("dir1/dir3");
    vcs.createFile("dir1/dir2/file", cf(""), -1);
    vcs.endChangeSet(null);

    vcs.createFile("dir1/dir3/file", null, -1);

    List<Revision> rr = vcs.getRevisionsFor("dir1");
    Revision recent = rr.get(0);
    Revision prev = rr.get(1);

    Difference d = prev.getDifferenceWith(recent);

    assertEquals("dir1", d.getLeft().getName());
    assertEquals(NOT_MODIFIED, d.getKind());
    assertEquals(1, d.getChildren().size());

    d = d.getChildren().get(0);

    assertEquals("dir3", d.getLeft().getName());
    assertEquals(NOT_MODIFIED, d.getKind());
    assertEquals(1, d.getChildren().size());
  }
}
