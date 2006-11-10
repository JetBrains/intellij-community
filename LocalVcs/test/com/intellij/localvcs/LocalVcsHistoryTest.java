package com.intellij.localvcs;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class LocalVcsHistoryTest extends TestCase {
  private LocalVcs vcs = new LocalVcs(new TestStorage());

  @Test
  public void testFileHistory() {
    vcs.createFile(p("file"), "content");
    vcs.apply();

    vcs.changeFileContent(p("file"), "new content");
    vcs.apply();

    assertEntiesContents(new String[]{"new content", "content"},
                         vcs.getEntryHistory(p("file")));
  }

  @Test
  public void testHistoryOfAnUnknownFile() {
    vcs.createFile(p("file"), "");
    vcs.apply();

    try {
      vcs.getEntryHistory(p("unknown file"));
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testHistoryOfDeletedFile() {
    vcs.createFile(p("file"), "content");
    vcs.apply();

    vcs.delete(p("file"));
    vcs.apply();

    // todo what should we return?
    try {
      vcs.getEntryHistory(p("file"));
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testDoesNotIncludeUnappliedChangesInHistory() {
    vcs.createFile(p("file"), "content");
    vcs.apply();

    vcs.changeFileContent(p("file"), "new content");

    assertEntiesContents(new String[]{"content"},
                         vcs.getEntryHistory(p("file")));
  }

  @Test
  public void testRenamingKeepsOldNameAndContent() {
    vcs.createFile(p("file"), "content");
    vcs.apply();

    vcs.rename(p("file"), "new file");
    vcs.apply();

    List<Entry> revs = vcs.getEntryHistory(p("new file"));

    assertEquals(2, revs.size());

    assertEquals(p("new file"), revs.get(0).getPath());
    assertEquals("content", revs.get(0).getContent());

    assertEquals(p("file"), revs.get(1).getPath());
    assertEquals("content", revs.get(1).getContent());
  }

  @Test
  public void testDeletingFileAndCreatingNewOneWithSameName() {
    vcs.createFile(p("file"), "old");
    vcs.apply();

    vcs.delete(p("file"));
    vcs.createFile(p("file"), "new");
    vcs.apply();

    assertEntiesContents(new String[]{"new"}, vcs.getEntryHistory(p("file")));
  }

  @Test
  public void testRenamingFileAndCreatingNewOneWithSameName() {
    vcs.createFile(p("file1"), "content1");
    vcs.apply();

    vcs.rename(p("file1"), "file2");
    vcs.createFile(p("file1"), "content2");
    vcs.apply();

    assertEntiesContents(new String[]{"content1", "content1"},
                         vcs.getEntryHistory(p("file2")));

    assertEntiesContents(new String[]{"content2"},
                         vcs.getEntryHistory(p("file1")));
  }

  @Test
  public void testGettingHistory() {
    vcs.createFile(p("file1"), "content1");
    vcs.createFile(p("file2"), "content2");
    vcs.apply();

    vcs.createFile(p("file3"), "content3");
    vcs.changeFileContent(p("file1"), "new content1");
    vcs.apply();

    Integer id1 = vcs.getEntry(p("file1")).getObjectId();
    Integer id2 = vcs.getEntry(p("file2")).getObjectId();
    Integer id3 = vcs.getEntry(p("file3")).getObjectId();

    List<RootEntry> rootEntries = vcs.getHistory();
    assertEquals(2, rootEntries.size());

    assertEntries(
        new Object[]{
            id1 + "file1" + "new content1",
            id2 + "file2" + "content2",
            id3 + "file3" + "content3"},
        rootEntries.get(0));

    assertEntries(
        new Object[]{
            id1 + "file1" + "content1",
            id2 + "file2" + "content2"},
        rootEntries.get(1));
  }

  private void assertEntries(Object[] expected, RootEntry root) {
    List<String> actual = new ArrayList<String>();
    for (Entry e : root.getChildren()) {
      actual.add(e.getObjectId() + e.getName() + e.getContent());
    }
    assertElements(expected, actual);
  }

  @Test
  public void testGettingHistoryOnCleanVcs() {
    assertTrue(vcs.getHistory().isEmpty());
  }

  @Test
  public void testLabelingEmptyLocalVcsThrowsException() {
    try {
      vcs.putLabel("label");
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testChangeList() {
    vcs.createFile(p("file1"), "content1");
    vcs.apply();
    vcs.putLabel("label1");

    vcs.createFile(p("file2"), "content2");
    vcs.apply();
    vcs.putLabel("label2");

    SnapshotList result = vcs.getSnapshotList();
    assertEquals(2, result.getSnapshot().size());

    assertEquals("label1", result.getSnapshot().get(0).getLabel());
    assertEquals("label2", result.getSnapshot().get(1).getLabel());
  }

  @Test
  public void testChangeListChanges() {
    vcs.createFile(p("file1"), "content1");
    vcs.createFile(p("file2"), "content2");
    vcs.apply();

    vcs.changeFileContent(p("file1"), "new content1");
    vcs.rename(p("file2"), "new file2");
    vcs.apply();

    SnapshotList result = vcs.getSnapshotList();
    Snapshot s1 = result.getSnapshot().get(0);
    Snapshot s2 = result.getSnapshot().get(1);

    assertTrue(s1.getDifferences().isEmpty());

    //assertEquals(2, s1.getDifferences().size());
  }
}
