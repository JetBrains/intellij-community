// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration;

import com.intellij.history.LocalHistory;
import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.tree.Entry;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TemporaryDirectory;
import com.intellij.testFramework.VfsTestUtil;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ChangesAndEntriesTest extends IntegrationTestCase {
  public void testChanges() throws Exception {
    VirtualFile f = createFile("file.txt", "old");
    loadContent(f);
    setContent(f, "new");

    List<ChangeSet> changes = getChangesFor(f);
    assertThat(changes).hasSize(2);
    assertContent("new", getCurrentEntry(f));
    assertContent("old", getEntryFor(changes.get(0), f));
  }

  public void testNamedAndUnnamedCauseActions() throws Exception {
    getVcs().beginChangeSet();
    VirtualFile f = createFile("file.txt");
    getVcs().endChangeSet("name");

    setContent(f, "foo");

    List<ChangeSet> changes = getChangesFor(f);
    assertThat(changes).hasSize(2);

    assertNull(changes.get(0).getName());
    assertEquals("name", changes.get(1).getName());
  }

  public void testEmptyChangesAfterPurge() throws Exception {
    Clock.setTime(10);
    VirtualFile f = createFile("file.txt", "content");
    loadContent(f);
    getVcs().getChangeListInTests().purgeObsolete(0);

    List<ChangeSet> changes = getChangesFor(f);
    assertEmpty(changes);

    Entry e = getCurrentEntry(f);
    assertEquals("file.txt", e.getName());
    assertContent("content", e);
    assertEquals(f.getTimeStamp(), e.getTimestamp());
  }

  public void testCurrentEntryForDirectoryAfterPurge() {
    Clock.setTime(10);
    VirtualFile f = createDirectory("dir");
    getVcs().getChangeListInTests().purgeObsolete(0);

    Entry e = getCurrentEntry(f);
    assertEquals(-1, e.getTimestamp()); // directory has no timestamp
    assertEquals("dir", e.getName());
  }

  public void testIncludingVersionBeforeFirstChangeAfterPurge() throws IOException {
    Clock.setTime(10);
    VirtualFile f = createFile("file.txt", "one");
    loadContent(f);
    Clock.setTime(20);
    setContent(f, "two");

    getVcs().getChangeListInTests().purgeObsolete(5);

    List<ChangeSet> changes = getChangesFor(f);
    assertEquals(1, changes.size());

    assertContent("two", getCurrentEntry(f));
    assertContent("one", getEntryFor(changes.get(0), f));
  }

  public void testDoesNotIncludeChangesForAnotherEntries() throws IOException {
    getVcs().beginChangeSet();
    createFile("file1.txt");
    getVcs().endChangeSet("1");

    getVcs().beginChangeSet();
    VirtualFile f2 = createFile("file2.txt");
    getVcs().endChangeSet("2");

    List<ChangeSet> changes = getChangesFor(f2);
    assertEquals(1, changes.size());
    assertEquals("2", changes.get(0).getName());
  }

  public void testChangesTimestamp() throws IOException {
    Clock.setTime(10);
    VirtualFile f = createFile("file1.txt");

    Clock.setTime(20);
    setContent(f, "a");

    Clock.setTime(30);
    setContent(f, "b");

    List<ChangeSet> changes = getChangesFor(f);
    assertEquals(30L, changes.get(0).getTimestamp());
    assertEquals(20L, changes.get(1).getTimestamp());
    assertEquals(10L, changes.get(2).getTimestamp());
  }

  public void testTimestampForCurrentEntryAfterPurgeFromCurrentTimestamp() throws IOException {
    VirtualFile f = createFile("file.txt");
    getVcs().getChangeListInTests().purgeObsolete(0);

    assertEquals(f.getTimeStamp(), getCurrentEntry(f).getTimestamp());
  }

  public void testTimestampForLastChangesAfterPurge() throws IOException {
    Clock.setTime(10);
    VirtualFile f = createFile("file1.txt");

    Clock.setTime(20);
    setContent(f, "a");

    Clock.setTime(30);
    setContent(f, "b");

    getVcs().getChangeListInTests().purgeObsolete(15);

    List<ChangeSet> changes = getChangesFor(f);
    assertEquals(30L, changes.get(0).getTimestamp());
    assertEquals(20L, changes.get(1).getTimestamp());
  }

  public void testChangesForFileCreatedWithSameNameAsDeletedOne() throws IOException {
    VirtualFile f = createFile("file.txt", "old");
    loadContent(f);
    VfsTestUtil.deleteFile(f);
    f = createFile("file.txt", "new");
    loadContent(f);

    List<ChangeSet> changes = getChangesFor(f);
    assertThat(changes).hasSize(3);

    Entry e = getCurrentEntry(f);
    assertThat(e.getName()).isEqualTo("file.txt");
    assertContent("new", e);

    assertThat(getEntryFor(changes.get(0), f)).isNull();

    e = getEntryFor(changes.get(1), f);
    assertThat(e.getName()).isEqualTo("file.txt");
    assertContent("old", e);

    assertThat(getEntryFor(changes.get(2), f)).isNull();
  }

  public void testChangesForDirectoryWithTheSameNameAsDeletedOne() {
    VirtualFile dir = createDirectory("dir");
    delete(dir);
    dir = createDirectory("dir");

    assertThat(getChangesFor(dir)).hasSize(3);
  }

  public void testChangesForRestoredDirectoryWithRestoreChildren() throws IOException {
    VirtualFile dir = createDirectory("dir");
    createFile("dir/f.txt");
    delete(dir);

    getVcs().beginChangeSet();
    dir = createDirectory("dir");
    VirtualFile f = createFile("dir/f.txt");
    getVcs().endChangeSet(null);

    List<ChangeSet> changes = getChangesFor(dir);
    assertEquals(4, changes.size());
    assertEquals(1, getCurrentEntry(dir).getChildren().size());
    assertNull(getEntryFor(changes.get(0), dir));
    assertEquals(1, getEntryFor(changes.get(1), dir).getChildren().size());
    assertEquals(0, getEntryFor(changes.get(2), dir).getChildren().size());

    assertThat(getChangesFor(f)).hasSize(3);
  }

  public void testChangesForFileThatWasCreatedAndDeletedInOneChangeSet() throws IOException {
    getVcs().beginChangeSet();
    VirtualFile f = createFile("f.txt");
    getVcs().endChangeSet("1");
    delete(f);

    getVcs().beginChangeSet();
    f = createFile("f.txt");
    delete(f);
    getVcs().endChangeSet("2");

    getVcs().beginChangeSet();
    f = createFile("f.txt");
    getVcs().endChangeSet("3");

    getVcs().beginChangeSet();
    delete(f);
    f = createFile("f.txt");
    getVcs().endChangeSet("4");

    List<ChangeSet> changes = getChangesFor(f);
    assertEquals(5, changes.size());
    assertEquals("4", changes.get(0).getName());
    assertEquals("3", changes.get(1).getName());
    assertEquals("2", changes.get(2).getName());
    assertNull(changes.get(3).getName());
    assertEquals("1", changes.get(4).getName());
  }

  public void testChangesForFileCreatedInPlaceOfRenamedOne() throws IOException {
    VirtualFile f = createFile("file1.txt", "content1");
    loadContent(f);
    rename(f, "file2.txt");
    VirtualFile ff = createFile("file1.txt", "content2");
    loadContent(ff);

    List<ChangeSet> changes = getChangesFor(ff);
    assertEquals(1, changes.size());

    Entry e = getCurrentEntry(ff);
    assertEquals("file1.txt", e.getName());
    assertContent("content2", e);

    changes = getChangesFor(f);
    assertEquals(2, changes.size());

    e = getCurrentEntry(f);
    assertEquals("file2.txt", e.getName());
    assertContent("content1", e);

    e = getEntryFor(changes.get(0), f);
    assertEquals("file1.txt", e.getName());
    assertContent("content1", e);
  }

  public void testChangesIfSomeFilesWereDeletedDuringChangeSet() throws IOException {
    VirtualFile dir = createDirectory("dir");
    VirtualFile f = createFile("dir/f.txt");
    getVcs().beginChangeSet();
    delete(f);

    List<ChangeSet> changes = getChangesFor(dir);
    assertEquals(3, changes.size());
  }

  public void testGettingEntryInRenamedDir() {
    VirtualFile dir = createDirectory("dir");
    VirtualFile f = TemporaryDirectory.createVirtualFile(dir, "file.txt", null);
    rename(dir, "newDir");
    setContent(f, "xxx");

    List<ChangeSet> changes = getChangesFor(f);
    assertEquals(3, changes.size());

    assertEquals(myRoot.getPath() + "/newDir/file.txt", getCurrentEntry(f).getPath());
    assertEquals(myRoot.getPath() + "/newDir/file.txt", getEntryFor(changes.get(0) ,f).getPath());
    assertEquals(myRoot.getPath() + "/dir/file.txt", getEntryFor(changes.get(1), f).getPath());
  }

  public void testGettingDifferenceBetweenEntries() throws IOException {
    VirtualFile f = createFile("file.txt", "one");
    loadContent(f);
    setContent(f, "two");

    List<ChangeSet> changes = getChangesFor(f);

    List<Difference> dd = Entry.getDifferencesBetween(getEntryFor(changes.get(0), f), getCurrentEntry(f), true);
    assertEquals(1, dd.size());

    Difference d = dd.get(0);
    assertContent("one", d.getLeft());
    assertContent("two", d.getRight());
  }

  public void testDifferenceForDirectory() throws IOException {
    VirtualFile dir = createDirectory("dir");
    createFile("dir/file.txt");

    List<ChangeSet> changes = getChangesFor(dir);
    assertEquals(2, changes.size());

    List<Difference> dd = Entry.getDifferencesBetween(getEntryFor(changes.get(0), dir), getCurrentEntry(dir), true);
    assertEquals(1, dd.size());

    Difference d = dd.get(0);
    assertNull(d.getLeft());
    assertEquals("file.txt", d.getRight().getName());
  }

  public void testNoDifferenceForDirectoryWithEqualContents() throws IOException {
    VirtualFile dir = createDirectory("dir");
    VirtualFile f = createFile("dir/file.txt");
    delete(f);

    List<ChangeSet> changes = getChangesFor(dir);

    assertTrue(Entry.getDifferencesBetween(getCurrentEntry(dir), getEntryFor(changes.get(1), dir)).isEmpty());
  }

  public void testDoesNotIncludeNotModifiedDifferences() throws IOException {
    getVcs().beginChangeSet();
    VirtualFile dir = createDirectory("dir1");
    createFile("dir1/dir2/file.txt");
    createDirectory("dir1/dir3");
    getVcs().endChangeSet(null);

    createFile("dir1/dir3/file.txt");

    List<ChangeSet> changes = getChangesFor(dir);
    List<Difference> dd = Entry.getDifferencesBetween(getEntryFor(changes.get(0), dir), getCurrentEntry(dir), true);
    assertEquals(1, dd.size());

    Difference d = dd.get(0);
    assertNull(d.getLeft());
    assertEquals(myRoot.getPath() + "/dir1/dir3/file.txt", d.getRight().getPath());
  }

  public void testFilteredChangesDoNotContainLabels() throws IOException {
    createFile("foo.txt");
    LocalHistory.getInstance().putSystemLabel(myProject, "1", -1);
    createFile("bar.txt");
    LocalHistory.getInstance().putSystemLabel(myProject, "2", -1);

    assertEquals(5, getChangesFor(myRoot).size());
    assertEquals(2, getChangesFor(myRoot, "*.txt").size());
  }

  public void testFilteredChangesIfNothingFound() throws Exception {
    createFile("foo.txt");
    assertEquals(2, getChangesFor(myRoot).size());
    assertEquals(0, getChangesFor(myRoot, "xxx").size());
  }

  public void testFilteredChangesPartialMatch() throws Exception {
    createFile("Hello");
    createFile("src/main/MyClass.java");
    assertEquals(3, getChangesFor(myRoot).size());
    assertEquals(1, getChangesFor(myRoot, "ell").size());
    assertEquals(1, getChangesFor(myRoot, "*ell").size());
    assertEquals(1, getChangesFor(myRoot, "s/m/MC").size());
  }

  public void testDoNotIncludeLabelsBeforeFirstChange() throws Exception {
    LocalHistory.getInstance().putSystemLabel(myProject, "1", -1);
    VirtualFile f = createFile("foo.txt");
    LocalHistory.getInstance().putSystemLabel(myProject, "2", -1);
    assertEquals(2, getChangesFor(f).size());
  }

  public void testDoNotIncludeLabelsWhenFileDidNotExist() throws Exception {
    VirtualFile f = createFile("foo.txt");
    LocalHistory.getInstance().putSystemLabel(myProject, "1", -1);
    delete(f);
    LocalHistory.getInstance().putSystemLabel(myProject, "2", -1);
    f = createFile("foo.txt");
    LocalHistory.getInstance().putSystemLabel(myProject, "3", -1);

    assertEquals(5, getChangesFor(f).size());
  }

  public void testDeleteAndRestoreInTheSameChangeSet() throws Exception {
    String fileName = "foo.txt";

    VirtualFile file = createFile(fileName);

    getVcs().beginChangeSet();
    delete(file);
    VirtualFile restoredFile = createFile(fileName);
    getVcs().endChangeSet("delete and create file");

    assertEquals(2, getChangesFor(restoredFile).size());
  }

  public void testRenameAndDeleteInTheSameChangeSet() throws Exception {
    String oldFileName = "old.foo.txt";
    String newFileName = "new.foo.txt";

    VirtualFile file = createFile(oldFileName);

    getVcs().beginChangeSet();
    rename(file, newFileName);
    delete(file);
    getVcs().endChangeSet("renamed and deleted file");
    VirtualFile restoredFile = createFile(newFileName);

    assertEquals(3, getChangesFor(restoredFile).size());
  }
}
