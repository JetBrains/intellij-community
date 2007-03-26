package com.intellij.localvcs;

import static com.intellij.localvcs.Difference.Kind.*;
import org.junit.Test;

import java.util.List;

public class LocalVcsHistoryTest extends LocalVcsTestCase {
  // todo difference on root does not work!!!
  TestLocalVcs vcs = new TestLocalVcs();

  @Test
  public void testTreatingSeveralChangesDuringChangeSetAsOne() {
    vcs.beginChangeSet();
    vcs.createDirectory("dir");
    vcs.createFile("dir/one", null, -1);
    vcs.createFile("dir/two", null, -1);
    vcs.endChangeSet(null);

    assertEquals(1, vcs.getLabelsFor("dir").size());
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

    assertEquals(5, vcs.getLabelsFor("dir").size());
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

    List<Label> ll = vcs.getLabelsFor("dir");
    assertEquals(1, ll.size());
    assertEquals("outer", ll.get(0).getName());
  }

  @Test
  public void testNamedAndUnnamedLables() {
    vcs.beginChangeSet();
    vcs.createFile("file", null, -1);
    vcs.endChangeSet("label");

    vcs.changeFileContent("file", null, -1);

    List<Label> ll = vcs.getLabelsFor("file");
    assertEquals(2, ll.size());

    assertNull(ll.get(0).getName());
    assertEquals("label", ll.get(1).getName());
  }

  @Test
  public void testIncludingCurrentVersionAfterPurge() {
    setCurrentTimestamp(10);
    vcs.createFile("file", null, -1);
    vcs.purgeUpTo(20);

    List<Label> ll = vcs.getLabelsFor("file");
    assertEquals(1, ll.size());

    assertEquals("file", ll.get(0).getEntry().getName());
  }

  @Test
  public void testTakingTimestampForCurrentLabelAtMomentOfGettingLabels() {
    setCurrentTimestamp(10);
    vcs.createFile("file", null, -1);
    vcs.purgeUpTo(20);

    setCurrentTimestamp(20);
    List<Label> ll = vcs.getLabelsFor("file");

    setCurrentTimestamp(30);
    assertEquals(20L, ll.get(0).getTimestamp());
  }

  @Test
  public void testLabelsTimestamp() {
    setCurrentTimestamp(10);
    vcs.createFile("file", null, -1);

    setCurrentTimestamp(20);
    vcs.changeFileContent("file", null, -1);

    List<Label> labels = vcs.getLabelsFor("file");
    assertEquals(20L, labels.get(0).getTimestamp());
    assertEquals(10L, labels.get(1).getTimestamp());
  }

  @Test
  public void testDoesNotIncludeLabelsForAnotherEntries() {
    vcs.beginChangeSet();
    vcs.createFile("file1", null, -1);
    vcs.endChangeSet("1");

    vcs.beginChangeSet();
    vcs.createFile("file2", null, -1);
    vcs.endChangeSet("2");

    List<Label> labels = vcs.getLabelsFor("file2");
    assertEquals(1, labels.size());
    assertEquals("2", labels.get(0).getName());
  }

  @Test
  public void testHistoryForFileCreatedWithSameNameAsDeletedOne() {
    vcs.createFile("file", b("old"), -1);
    vcs.delete("file");
    vcs.createFile("file", b("new"), -1);

    List<Label> labels = vcs.getLabelsFor("file");
    assertEquals(1, labels.size());

    Entry e = labels.get(0).getEntry();
    assertEquals("file", e.getName());
    assertEquals(c("new"), e.getContent());
  }

  @Test
  public void testHistoryForFileCreatenInPlaceOfRenamedOne() {
    vcs.createFile("file1", b("content1"), -1);
    vcs.rename("file1", "file2");
    vcs.createFile("file1", b("content2"), -1);

    List<Label> labels = vcs.getLabelsFor("file1");
    assertEquals(1, labels.size());

    Entry e = labels.get(0).getEntry();
    assertEquals("file1", e.getName());
    assertEquals(c("content2"), e.getContent());

    labels = vcs.getLabelsFor("file2");
    assertEquals(2, labels.size());

    e = labels.get(0).getEntry();
    assertEquals("file2", e.getName());
    assertEquals(c("content1"), e.getContent());

    e = labels.get(1).getEntry();
    assertEquals("file1", e.getName());
    assertEquals(c("content1"), e.getContent());
  }

  @Test
  public void testGettingEntryFromLabel() {
    vcs.createFile("file", b("content"), 123L);
    vcs.changeFileContent("file", b("new content"), 456L);

    List<Label> labels = vcs.getLabelsFor("file");

    Entry e = labels.get(0).getEntry();
    assertEquals("file", e.getName());
    assertEquals(c("new content"), e.getContent());
    assertEquals(456L, e.getTimestamp());

    e = labels.get(1).getEntry();
    assertEquals("file", e.getName());
    assertEquals(c("content"), e.getContent());
    assertEquals(123L, e.getTimestamp());
  }

  @Test
  public void testGettingEntryFromLabelInRenamedDir() {
    vcs.createDirectory("dir");
    vcs.createFile("dir/file", null, -1);
    vcs.rename("dir", "newDir");
    vcs.changeFileContent("newDir/file", null, -1);

    List<Label> labels = vcs.getLabelsFor("newDir/file");
    assertEquals(2, labels.size());

    assertEquals("newDir/file", labels.get(0).getEntry().getPath());
    assertEquals("dir/file", labels.get(1).getEntry().getPath());
  }

  @Test
  public void testGettingEntryFromLabelDoesNotChangeRootEntry() {
    vcs.createFile("file", b("content"), -1);
    vcs.changeFileContent("file", b("new content"), -1);

    List<Label> labels = vcs.getLabelsFor("file");

    assertEquals(c("content"), labels.get(1).getEntry().getContent());
    assertEquals(c("new content"), vcs.getEntry("file").getContent());
  }

  @Test
  public void testGettingDifferenceBetweenLablels() {
    vcs.createFile("file", b("content"), -1);
    vcs.changeFileContent("file", b("new content"), -1);

    List<Label> labels = vcs.getLabelsFor("file");

    Label recent = labels.get(0);
    Label prev = labels.get(1);

    Difference d = prev.getDifferenceWith(recent);
    assertEquals(MODIFIED, d.getKind());
    assertEquals(c("content"), d.getLeft().getContent());
    assertEquals(c("new content"), d.getRight().getContent());
  }

  @Test
  public void testNoDifferenceBetweenLabels() {
    vcs.createFile("file", b("content"), -1);

    List<Label> labels = vcs.getLabelsFor("file");

    Label one = labels.get(0);
    Label two = labels.get(0);

    Difference d = one.getDifferenceWith(two);
    assertFalse(d.hasDifference());
  }

  @Test
  public void testDifferenceForDirectory() {
    vcs.createDirectory("dir");
    vcs.createFile("dir/file", null, -1);

    List<Label> labels = vcs.getLabelsFor("dir");
    assertEquals(2, labels.size());

    Label recent = labels.get(0);
    Label prev = labels.get(1);

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

    List<Label> labels = vcs.getLabelsFor("dir");

    Difference d = labels.get(0).getDifferenceWith(labels.get(2));
    assertFalse(d.hasDifference());
  }

  @Test
  public void testDoesNotIncludeNotModifiedDifferences() {
    vcs.beginChangeSet();
    vcs.createDirectory("dir1");
    vcs.createDirectory("dir1/dir2");
    vcs.createDirectory("dir1/dir3");
    vcs.createFile("dir1/dir2/file", b(""), -1);
    vcs.endChangeSet(null);

    vcs.createFile("dir1/dir3/file", null, -1);

    List<Label> labels = vcs.getLabelsFor("dir1");
    Label recent = labels.get(0);
    Label prev = labels.get(1);

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
