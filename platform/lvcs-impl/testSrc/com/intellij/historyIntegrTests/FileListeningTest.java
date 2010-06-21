/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.historyIntegrTests;


import com.intellij.history.core.changes.Change;
import com.intellij.history.core.changes.DeleteChange;
import com.intellij.history.core.changes.StructuralChange;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.utils.RunnableAdapter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.util.SmartList;
import com.intellij.util.io.ReadOnlyAttributeUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class FileListeningTest extends IntegrationTestCase {
  public void testCreatingFiles() throws Exception {
    VirtualFile f = createFile("file.txt");
    assertEquals(2, getRevisionsFor(f).size());
  }

  public void testCreatingDirectories() throws Exception {
    VirtualFile f = createDirectory("dir");
    assertEquals(2, getRevisionsFor(f).size());
  }

  public void testIgnoringFilteredFileTypes() throws Exception {
    int before = getRevisionsFor(myRoot).size();
    createFile("file.hprof");

    assertEquals(before, getRevisionsFor(myRoot).size());
  }

  public void testIgnoringFilteredDirectories() throws Exception {
    int before = getRevisionsFor(myRoot).size();

    createDirectory(FILTERED_DIR_NAME);
    assertEquals(before, getRevisionsFor(myRoot).size());
  }

  public void testIgnoringFilesRecursively() throws Exception {
    addExcludedDir(myRoot.getPath() + "/dir/subdir");
    addContentRoot(createModule("foo"), myRoot.getPath() + "/dir/subdir/subdir2");

    String dir1 = createDirectoryExternally("dir");
    String f1 = createFileExternally("dir/f.txt");
    createFileExternally("dir/f.class");
    createFileExternally("dir/subdir/f.txt");
    String dir2 = createDirectoryExternally("dir/subdir/subdir2");
    String f2 = createFileExternally("dir/subdir/subdir2/f.txt");

    LocalFileSystem.getInstance().refresh(false);

    List<Change> changes = getVcs().getChangeListInTests().getChangesInTests().get(0).getChanges();
    assertEquals(4, changes.size());
    List<String> actual = new SmartList<String>();
    for (Change each : changes) {
      actual.add(((StructuralChange)each).getPath());
    }

    List<String> expected = new ArrayList<String>(Arrays.asList(dir1, dir2, f1, f2));

    Collections.sort(actual);
    Collections.sort(expected);
    assertOrderedEquals(actual, expected);
  }

  public void testChangingFileContent() throws Exception {
    VirtualFile f = createFile("file.txt");
    assertEquals(2, getRevisionsFor(f).size());

    f.setBinaryContent(new byte[]{1});
    assertEquals(3, getRevisionsFor(f).size());

    f.setBinaryContent(new byte[]{2});
    assertEquals(4, getRevisionsFor(f).size());
  }

  public void testRenamingFile() throws Exception {
    VirtualFile f = createFile("file.txt");
    assertEquals(2, getRevisionsFor(f).size());

    f.rename(null, "file2.txt");
    assertEquals(3, getRevisionsFor(f).size());
  }

  public void testRenamingFileOnlyAfterRenamedEvent() throws Exception {
    final VirtualFile f = createFile("old.txt");

    final int[] log = new int[2];
    VirtualFileListener l = new VirtualFileAdapter() {
      public void beforePropertyChange(VirtualFilePropertyEvent e) {
        log[0] = getRevisionsFor(f).size();
      }

      public void propertyChanged(VirtualFilePropertyEvent e) {
        log[1] = getRevisionsFor(f).size();
      }
    };

    assertEquals(2, getRevisionsFor(f).size());

    addFileListenerDuring(l, new RunnableAdapter() {
      @Override
      public void doRun() throws IOException {
        f.rename(null, "new.txt");
      }
    });

    assertEquals(2, log[0]);
    assertEquals(3, log[1]);
  }

  public void testRenamingFilteredFileToNonFiltered() throws Exception {
    int before = getRevisionsFor(myRoot).size();

    VirtualFile f = createFile("file.hprof");
    assertEquals(before, getRevisionsFor(myRoot).size());

    f.rename(null, "file.txt");
    assertEquals(before + 1, getRevisionsFor(myRoot).size());

    assertEquals(2, getRevisionsFor(f).size());
  }

  public void testRenamingNonFilteredFileToFiltered() throws Exception {
    int before = getRevisionsFor(myRoot).size();

    VirtualFile f = createFile("file.txt");
    assertEquals(before + 1, getRevisionsFor(myRoot).size());

    f.rename(null, "file.hprof");
    assertEquals(before + 2, getRevisionsFor(myRoot).size());
  }

  public void testRenamingFilteredDirectoriesToNonFiltered() throws Exception {
    int before = getRevisionsFor(myRoot).size();

    VirtualFile f = createFile(FILTERED_DIR_NAME);
    assertEquals(before, getRevisionsFor(myRoot).size());

    f.rename(null, "not_filtered");
    assertEquals(before + 1, getRevisionsFor(myRoot).size());

    assertEquals(2, getRevisionsFor(f).size());
  }

  public void testRenamingNonFilteredDirectoriesToFiltered() throws Exception {
    int before = getRevisionsFor(myRoot).size();

    VirtualFile f = createDirectory("not_filtered");
    assertEquals(before + 1, getRevisionsFor(myRoot).size());

    f.rename(null, FILTERED_DIR_NAME);
    assertEquals(before + 2, getRevisionsFor(myRoot).size());
  }

  public void testChangingROStatusForFile() throws Exception {
    VirtualFile f = createFile("f.txt");
    assertEquals(2, getRevisionsFor(f).size());

    ReadOnlyAttributeUtil.setReadOnlyAttribute(f, true);
    assertEquals(3, getRevisionsFor(f).size());

    ReadOnlyAttributeUtil.setReadOnlyAttribute(f, false);
    assertEquals(4, getRevisionsFor(f).size());
  }

  public void testIgnoringROStstusChangeForUnversionedFiles() throws Exception {
    int before = getRevisionsFor(myRoot).size();

    VirtualFile f = createFile("f.hprof");
    ReadOnlyAttributeUtil.setReadOnlyAttribute(f, true); // shouldn't throw

    assertEquals(before, getRevisionsFor(myRoot).size());
  }

  public void testDeletion() throws Exception {
    VirtualFile f = createDirectory("f.txt");

    int before = getRevisionsFor(myRoot).size();

    f.delete(null);
    assertEquals(before + 1, getRevisionsFor(myRoot).size());
  }

  public void testDeletionOfFilteredDirectoryDoesNotThrowsException() throws Exception {
    int before = getRevisionsFor(myRoot).size();

    VirtualFile f = createDirectory(FILTERED_DIR_NAME);
    f.delete(null);
    assertEquals(before, getRevisionsFor(myRoot).size());
  }

  public void testDeletionDoesNotVersionIgnoredFilesRecursively() throws Exception {
    String dir1 = createDirectoryExternally("dir");
    String f1 = createFileExternally("dir/f.txt");
    createFileExternally("dir/f.class");
    createFileExternally("dir/subdir/f.txt");
    String dir2 = createDirectoryExternally("dir/subdir/subdir2");
    String f2 = createFileExternally("dir/subdir/subdir2/f.txt");

    LocalFileSystem.getInstance().refresh(false);

    addExcludedDir(myRoot.getPath() + "/dir/subdir");
    addContentRoot(myRoot.getPath() + "/dir/subdir/subdir2");

    LocalFileSystem.getInstance().findFileByPath(dir1).delete(this);

    List<Change> changes = getVcs().getChangeListInTests().getChangesInTests().get(0).getChanges();
    assertEquals(1, changes.size());
    Entry e = ((DeleteChange)changes.get(0)).getDeletedEntry();
    final List<Entry> children = e.getChildren();
    sortEntries(children);
    assertEquals(2, children.size());
    assertEquals("f.txt", children.get(0).getName());
    assertEquals("subdir", children.get(1).getName());
    assertEquals(1, children.get(1).getChildren().size());
    assertEquals("subdir2", children.get(1).getChildren().get(0).getName());
  }

  public void testCreationAndDeletionOfUnversionedFile() throws IOException {
    addExcludedDir(myRoot.getPath() + "/dir");

    Module m = createModule("foo");
    addContentRoot(m, myRoot.getPath() + "/dir/subDir");

    createFileExternally("dir/subDir/file.txt");
    LocalFileSystem.getInstance().refresh(false);

    FileUtil.delete(new File(myRoot.getPath() + "/dir"));
    LocalFileSystem.getInstance().refresh(false);

    createFileExternally("dir/subDir/file.txt");
    LocalFileSystem.getInstance().refresh(false);

    List<Revision> revs = getRevisionsFor(myRoot);
    assertEquals(4, revs.size());
    assertNotNull(revs.get(0).findEntry().findEntry("dir/subDir/file.txt"));
    assertNull(revs.get(1).findEntry().findEntry("dir/subDir/file.txt"));
    assertNotNull(revs.get(2).findEntry().findEntry("dir/subDir/file.txt"));
    assertNull(revs.get(3).findEntry().findEntry("dir/subDir/file.txt"));
  }

  public void testCreationAndDeletionOfFileUnderUnversionedDir() throws IOException {
    addExcludedDir(myRoot.getPath() + "/dir");

    Module m = createModule("foo");
    addContentRoot(m, myRoot.getPath() + "/dir/subDir");

    createFileExternally("dir/subDir/file.txt");
    LocalFileSystem.getInstance().refresh(false);

    FileUtil.delete(new File(myRoot.getPath() + "/dir/subDir"));
    LocalFileSystem.getInstance().refresh(false);

    createFileExternally("dir/subDir/file.txt");
    LocalFileSystem.getInstance().refresh(false);

    List<Revision> revs = getRevisionsFor(myRoot);
    assertEquals(4, revs.size());
    assertNotNull(revs.get(0).findEntry().findEntry("dir/subDir/file.txt"));
    assertNull(revs.get(1).findEntry().findEntry("dir/subDir"));
    assertNotNull(revs.get(2).findEntry().findEntry("dir/subDir/file.txt"));
    assertNull(revs.get(3).findEntry().findEntry("dir/subDir"));
  }

  private static void sortEntries(final List<Entry> entries) {
    Collections.sort(entries, new Comparator<Entry>() {
      public int compare(Entry o1, Entry o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
  }
}
