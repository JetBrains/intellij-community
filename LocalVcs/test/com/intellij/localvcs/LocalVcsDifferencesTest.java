package com.intellij.localvcs;

import java.util.List;

import static com.intellij.localvcs.Difference.Kind.CREATED;
import static com.intellij.localvcs.Difference.Kind.MODIFIED;
import static com.intellij.localvcs.Difference.Kind.NOT_MODIFIED;
import org.junit.Test;

public class LocalVcsDifferencesTest extends TestCase {
  // todo test difference on root does not work!!!
  private LocalVcs vcs = new LocalVcs(new TestStorage());

  @Test
  public void testLabelingEmptyLocalVcsThrowsException() {
    // todo is it true?
    try {
      vcs.putLabel("label");
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testGettingLabelsForAnUnknownFileThrowsException() {
    try {
      vcs.getLabelsFor("unknown file");
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testNamedAndUnnamedLables() {
    vcs.createFile("file", null, null);
    vcs.apply();
    vcs.putLabel("label");

    vcs.changeFileContent("file", null, null);
    vcs.apply();

    List<Label> labels = vcs.getLabelsFor("file");
    assertEquals(2, labels.size());

    assertEquals("label", labels.get(0).getName());
    assertNull(labels.get(1).getName());
  }

  @Test
  public void testDoesNotIncludeLabelsForAnotherEntries() {
    vcs.createFile("file1", null, null);
    vcs.apply();
    vcs.putLabel("1");

    vcs.createFile("file2", null, null);
    vcs.apply();
    vcs.putLabel("2");

    List<Label> labels = vcs.getLabelsFor("file2");
    assertEquals(1, labels.size());
    assertEquals("2", labels.get(0).getName());
  }

  @Test
  public void testTreatingDeletedAndCreatedFilesWithSameNameDifferently() {
    vcs.createFile("file", "old", null);
    vcs.apply();

    vcs.delete("file");
    vcs.createFile("file", "new", null);
    vcs.apply();

    List<Label> labels = vcs.getLabelsFor("file");
    assertEquals(1, labels.size());

    Entry e = labels.get(0).getEntry();
    assertEquals("file", e.getName());
    assertEquals("new", e.getContent());
  }

  @Test
  public void testTreatingRenamedAndCreatedFilesWithSameNameDifferently() {
    vcs.createFile("file1", "content1", null);
    vcs.apply();

    vcs.rename("file1", "file2", null);
    vcs.createFile("file1", "content2", null);
    vcs.apply();

    List<Label> labels = vcs.getLabelsFor("file1");
    assertEquals(1, labels.size());

    Entry e = labels.get(0).getEntry();
    assertEquals("file1", e.getName());
    assertEquals("content2", e.getContent());

    labels = vcs.getLabelsFor("file2");
    assertEquals(2, labels.size());

    e = labels.get(0).getEntry();
    assertEquals("file1", e.getName());
    assertEquals("content1", e.getContent());

    e = labels.get(1).getEntry();
    assertEquals("file2", e.getName());
    assertEquals("content1", e.getContent());
  }

  @Test
  public void testGettingEntryFromLabel() {
    vcs.createFile("file", "content", 123L);
    vcs.apply();

    vcs.changeFileContent("file", "new content", 456L);
    vcs.apply();

    List<Label> labels = vcs.getLabelsFor("file");

    Entry e = labels.get(0).getEntry();
    assertEquals("file", e.getName());
    assertEquals("content", e.getContent());
    assertEquals(123L, e.getTimestamp());

    e = labels.get(1).getEntry();
    assertEquals("file", e.getName());
    assertEquals("new content", e.getContent());
    assertEquals(456L, e.getTimestamp());
  }

  @Test
  public void testGettingEntryFromLabelDoesNotChangeRootEntry() {
    vcs.createFile("file", "content", null);
    vcs.apply();
    vcs.changeFileContent("file", "new content", null);
    vcs.apply();

    List<Label> labels = vcs.getLabelsFor("file");

    assertEquals("content", labels.get(0).getEntry().getContent());
    assertEquals("new content", vcs.getEntry("file").getContent());
  }

  @Test
  public void testGettingDifferenceBetweenLablels() {
    vcs.createFile("file", "content", null);
    vcs.apply();

    vcs.changeFileContent("file", "new content", null);
    vcs.apply();

    List<Label> labels = vcs.getLabelsFor("file");

    Label one = labels.get(0);
    Label two = labels.get(1);

    // todo can we calculate diffs by timestamp?
    Difference d = one.getDifferenceWith(two);
    assertEquals(MODIFIED, d.getKind());
    assertEquals("content", d.getLeft().getContent());
    assertEquals("new content", d.getRight().getContent());
  }

  @Test
  public void testNoDifferenceBetweenLabels() {
    vcs.createFile("file", "content", null);
    vcs.apply();

    List<Label> labels = vcs.getLabelsFor("file");

    Label one = labels.get(0);
    Label two = labels.get(0);

    Difference d = one.getDifferenceWith(two);
    assertFalse(d.hasDifference());
  }

  @Test
  public void testDifferenceForDirectory() {
    vcs.createDirectory("dir", null);
    vcs.apply();

    vcs.createFile("dir/file", null, null);
    vcs.apply();

    List<Label> labels = vcs.getLabelsFor("dir");
    assertEquals(2, labels.size());

    Label one = labels.get(0);
    Label two = labels.get(1);

    Difference d = one.getDifferenceWith(two);
    assertEquals(NOT_MODIFIED, d.getKind());
    assertEquals(1, d.getChildren().size());

    d = d.getChildren().get(0);
    assertEquals(CREATED, d.getKind());
    assertNull(d.getLeft());
    assertEquals("file", d.getRight().getName());
  }

  @Test
  public void testNoDifferenceForDirectoryWithRestoredContent() {
    vcs.createDirectory("dir", null);
    vcs.apply();

    vcs.createFile("dir/file", null, null);
    vcs.apply();

    vcs.delete("dir/file");
    vcs.apply();

    List<Label> labels = vcs.getLabelsFor("dir");

    Difference d = labels.get(0).getDifferenceWith(labels.get(2));
    assertFalse(d.hasDifference());
  }
}
