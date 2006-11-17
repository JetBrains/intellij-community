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
      vcs.getLabelsFor(p("unknown file"));
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testNamedAndUnnamedLables() {
    vcs.createFile(p("file"), null);
    vcs.apply();
    vcs.putLabel("label");

    vcs.changeFileContent(p("file"), null);
    vcs.apply();

    List<Label> labels = vcs.getLabelsFor(p("file"));
    assertEquals(2, labels.size());

    assertEquals("label", labels.get(0).getName());
    assertNull(labels.get(1).getName());
  }

  @Test
  public void testDoesNotIncludeLabelsForAnotherEntries() {
    vcs.createFile(p("file1"), null);
    vcs.apply();
    vcs.putLabel("1");

    vcs.createFile(p("file2"), null);
    vcs.apply();
    vcs.putLabel("2");

    List<Label> labels = vcs.getLabelsFor(p("file2"));
    assertEquals(1, labels.size());
    assertEquals("2", labels.get(0).getName());
  }

  @Test
  public void testTreatingDeletedAndCreatedFilesWithSameNameDifferently() {
    vcs.createFile(p("file"), "old");
    vcs.apply();

    vcs.delete(p("file"));
    vcs.createFile(p("file"), "new");
    vcs.apply();

    List<Label> labels = vcs.getLabelsFor(p("file"));
    assertEquals(1, labels.size());

    Entry e = labels.get(0).getEntry();
    assertEquals("file", e.getName());
    assertEquals("new", e.getContent());
  }

  @Test
  public void testTreatingRenamedAndCreatedFilesWithSameNameDifferently() {
    vcs.createFile(p("file1"), "content1");
    vcs.apply();

    vcs.rename(p("file1"), "file2");
    vcs.createFile(p("file1"), "content2");
    vcs.apply();

    List<Label> labels = vcs.getLabelsFor(p("file1"));
    assertEquals(1, labels.size());

    Entry e = labels.get(0).getEntry();
    assertEquals("file1", e.getName());
    assertEquals("content2", e.getContent());

    labels = vcs.getLabelsFor(p("file2"));
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
    vcs.createFile(p("file"), "content");
    vcs.apply();

    vcs.changeFileContent(p("file"), "new content");
    vcs.apply();

    List<Label> labels = vcs.getLabelsFor(p("file"));

    Entry e = labels.get(0).getEntry();
    assertEquals("file", e.getName());
    assertEquals("content", e.getContent());

    e = labels.get(1).getEntry();
    assertEquals("file", e.getName());
    assertEquals("new content", e.getContent());
  }

  @Test
  public void testGettingEntryFromLabelDoesNotChangeRootEntry() {
    vcs.createFile(p("file"), "content");
    vcs.apply();
    vcs.changeFileContent(p("file"), "new content");
    vcs.apply();

    List<Label> labels = vcs.getLabelsFor(p("file"));

    assertEquals("content", labels.get(0).getEntry().getContent());
    assertEquals("new content", vcs.getEntry(p("file")).getContent());
  }

  @Test
  public void testGettingDifferenceBetweenLablels() {
    vcs.createFile(p("file"), "content");
    vcs.apply();

    vcs.changeFileContent(p("file"), "new content");
    vcs.apply();

    List<Label> labels = vcs.getLabelsFor(p("file"));

    Label one = labels.get(0);
    Label two = labels.get(1);

    Difference d = one.getDifferenceWith(two);
    assertEquals(MODIFIED, d.getKind());
    assertEquals("content", d.getLeft().getContent());
    assertEquals("new content", d.getRight().getContent());
  }

  @Test
  public void testNoDifferenceBetweenLabels() {
    vcs.createFile(p("file"), "content");
    vcs.apply();

    List<Label> labels = vcs.getLabelsFor(p("file"));

    Label one = labels.get(0);
    Label two = labels.get(0);

    Difference d = one.getDifferenceWith(two);
    assertFalse(d.hasDifference());
  }

  @Test
  public void testDifferenceForDirectory() {
    vcs.createDirectory(p("dir"));
    vcs.apply();

    vcs.createFile(p("dir/file"), null);
    vcs.apply();

    List<Label> labels = vcs.getLabelsFor(p("dir"));
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
    vcs.createDirectory(p("dir"));
    vcs.apply();

    vcs.createFile(p("dir/file"), null);
    vcs.apply();

    vcs.delete(p("dir/file"));
    vcs.apply();

    List<Label> labels = vcs.getLabelsFor(p("dir"));

    Difference d = labels.get(0).getDifferenceWith(labels.get(2));
    assertFalse(d.hasDifference());
  }
}
