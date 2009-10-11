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

package com.intellij.historyIntegrTests.revertion;

import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.revertion.DifferenceReverter;
import com.intellij.historyIntegrTests.IntegrationTestCase;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DifferenceReverterTest extends IntegrationTestCase {
  public void testFileCreation() throws Exception {
    root.createChildData(this, "foo.txt");

    revertLastChange();

    assertNull(root.findChild("foo.txt"));
  }

  public void testFileDeletion() throws Exception {
    VirtualFile f = root.createChildData(this, "foo.txt");
    f.setBinaryContent(new byte[]{123}, -1, 4000);
    f.delete(this);

    revertLastChange();

    f = root.findChild("foo.txt");
    assertNotNull(f);
    assertEquals(123, f.contentsToByteArray()[0]);
    assertEquals(4000, f.getTimeStamp());
  }

  public void testDirDeletion() throws Exception {
    VirtualFile dir = root.createChildDirectory(this, "dir");
    VirtualFile subdir = dir.createChildDirectory(this, "subdir");
    VirtualFile f = subdir.createChildData(this, "foo.txt");
    f.setBinaryContent(new byte[]{123}, -1, 4000);

    dir.delete(this);

    revertLastChange();

    dir = root.findChild("dir");
    subdir = dir.findChild("subdir");
    f = subdir.findChild("foo.txt");
    assertNotNull(f);
    assertEquals(123, f.contentsToByteArray()[0]);
    assertEquals(4000, f.getTimeStamp());
  }

  public void testDeletionOfFileAndCreationOfDirAtTheSameTime() throws Exception {
    VirtualFile f = root.createChildData(this, "foo.txt");

    getVcs().beginChangeSet();
    f.delete(this);
    root.createChildDirectory(this, "foo.txt");
    getVcs().endChangeSet(null);

    revertLastChange();

    f = root.findChild("foo.txt");
    assertNotNull(f);
    assertFalse(f.isDirectory());
  }

  public void testDeletionOfDirAndCreationOfFileAtTheSameTime() throws Exception {
    VirtualFile f = root.createChildDirectory(this, "foo.txt");

    getVcs().beginChangeSet();
    f.delete(this);
    root.createChildData(this, "foo.txt");
    getVcs().endChangeSet(null);

    revertLastChange();

    f = root.findChild("foo.txt");
    assertNotNull(f);
    assertTrue(f.isDirectory());
  }

  public void testRename() throws Exception {
    VirtualFile f = root.createChildData(this, "foo.txt");
    f.setBinaryContent(new byte[]{123}, -1, 4000);
    f.rename(this, "bar.txt");

    revertLastChange();

    assertNull(root.findChild("bar.txt"));
    f = root.findChild("foo.txt");
    assertNotNull(f);
    assertEquals(123, f.contentsToByteArray()[0]);
    assertEquals(4000, f.getTimeStamp());
  }

  public void testMovement() throws Exception {
    VirtualFile dir1 = root.createChildDirectory(this, "dir1");
    VirtualFile dir2 = root.createChildDirectory(this, "dir2");

    VirtualFile f = dir1.createChildData(this, "foo.txt");
    f.setBinaryContent(new byte[]{123}, -1, 4000);

    f.move(this, dir2);

    revertLastChange();

    assertNull(dir2.findChild("foo.txt"));
    f = dir1.findChild("foo.txt");
    assertNotNull(f);
    assertEquals(123, f.contentsToByteArray()[0]);
    assertEquals(4000, f.getTimeStamp());
  }

  public void testParentRename() throws Exception {
    VirtualFile dir = root.createChildDirectory(this, "dir");
    VirtualFile f = dir.createChildData(this, "foo.txt");
    f.setBinaryContent(new byte[]{123}, -1, 4000);

    dir.rename(this, "dir2");

    revertLastChange();

    assertNull(root.findChild("dir2"));
    dir = root.findChild("dir");
    f = dir.findChild("foo.txt");
    assertNotNull(f);
    assertEquals(123, f.contentsToByteArray()[0]);
    assertEquals(4000, f.getTimeStamp());
  }

  public void testParentAndChildRename() throws Exception {
    VirtualFile dir = root.createChildDirectory(this, "dir");
    VirtualFile f = dir.createChildData(this, "foo.txt");
    f.setBinaryContent(new byte[]{123}, -1, 4000);

    getVcs().beginChangeSet();
    dir.rename(this, "dir2");
    f.rename(this, "bar.txt");
    getVcs().endChangeSet(null);

    revertLastChange();

    assertNull(root.findChild("dir2"));
    dir = root.findChild("dir");

    assertNull(dir.findChild("bar.txt"));
    f = dir.findChild("foo.txt");
    assertNotNull(f);
    assertEquals(123, f.contentsToByteArray()[0]);
    assertEquals(4000, f.getTimeStamp());
  }

  public void testRevertOnlyChildWhenParentAndChildRenamed() throws Exception {
    VirtualFile dir = root.createChildDirectory(this, "dir");
    VirtualFile f = dir.createChildData(this, "foo.txt");
    f.setBinaryContent(new byte[]{123}, -1, 4000);

    getVcs().beginChangeSet();
    dir.rename(this, "dir2");
    f.rename(this, "bar.txt");
    getVcs().endChangeSet(null);

    revertLastChange(1);

    assertNull(root.findChild("dir"));
    dir = root.findChild("dir2");
    assertNotNull(dir);

    assertNull(dir.findChild("bar.txt"));
    f = dir.findChild("foo.txt");
    assertNotNull(f);
    assertEquals(123, f.contentsToByteArray()[0]);
    assertEquals(4000, f.getTimeStamp());
  }

  public void testRevertContentChange() throws Exception {
    VirtualFile f = root.createChildData(this, "foo.txt");
    f.setBinaryContent(new byte[]{1}, -1, 1000);
    f.setBinaryContent(new byte[]{2}, -1, 2000);

    revertLastChange();

    f = root.findChild("foo.txt");
    assertNotNull(f);
    assertEquals(1, f.contentsToByteArray()[0]);
    assertEquals(1000, f.getTimeStamp());
  }

  public void testContentChangeWhenDirectoryExists() throws Exception {
    VirtualFile f = root.createChildData(this, "foo.txt");
    f.setBinaryContent(new byte[]{1}, -1, 1000);

    getVcs().beginChangeSet();
    f.rename(this, "bar.txt");
    f.setBinaryContent(new byte[]{2}, -1, 2000);
    root.createChildDirectory(this, "foo.txt");
    getVcs().endChangeSet(null);

    revertLastChange(1);

    assertNull(root.findChild("bar.txt"));
    f = root.findChild("foo.txt");
    assertNotNull(f);
    assertFalse(f.isDirectory());
    assertEquals(1, f.contentsToByteArray()[0]);
    assertEquals(1000, f.getTimeStamp());
  }

  public void testRevertingFromOldRevisionsWhenFileAlreadyDeleted() throws Exception {
    VirtualFile f = root.createChildData(this, "foo.txt");
    f.delete(this);

    revertChange(1);

    assertNull(root.findChild("foo.txt"));
  }

  public void testRevertingFromOldRevisionsWhenFileAlreadyExists() throws Exception {
    VirtualFile f = root.createChildData(this, "foo.txt");
    f.delete(this);
    f = root.createChildData(this, "foo.txt");

    revertChange(1);

    assertSame(f, root.findChild("foo.txt"));
  }

  public void testRevertingRenameFromOldRevisionsWhenDirDoesNotExists() throws Exception {
    VirtualFile dir = root.createChildDirectory(this, "dir");
    VirtualFile f = dir.createChildData(this, "foo.txt");

    f.rename(this, "bar.txt");

    dir.delete(this);

    revertChange(1);

    dir = root.findChild("dir");
    assertNotNull(dir);
    assertNotNull(dir.findChild("foo.txt"));
    assertNull(dir.findChild("bar.txt"));
  }

  public void testRevertingMoveFromOldRevisionsWhenDirDoesNotExists() throws Exception {
    VirtualFile dir1 = root.createChildDirectory(this, "dir1");
    VirtualFile dir2 = root.createChildDirectory(this, "dir2");
    VirtualFile f = dir1.createChildData(this, "foo.txt");

    f.move(this, dir2);

    dir1.delete(this);
    dir2.delete(this);

    revertChange(2);

    dir1 = root.findChild("dir1");
    assertNotNull(dir1);
    assertNull(root.findChild("dir2"));
    assertNotNull(dir1.findChild("foo.txt"));
  }

  public void testRevertingContentChangeFromOldRevisionsWhenDirDoesNotExists() throws Exception {
    VirtualFile dir = root.createChildDirectory(this, "dir");
    VirtualFile f = dir.createChildData(this, "foo.txt");

    f.setBinaryContent(new byte[]{1}, -1, 1000);
    f.setBinaryContent(new byte[]{2}, -1, 2000);

    dir.delete(this);

    revertChange(1);

    dir = root.findChild("dir");
    assertNotNull(dir);
    f = dir.findChild("foo.txt");
    assertNotNull(f);
    assertEquals(1, f.contentsToByteArray()[0]);
    assertEquals(1000, f.getTimeStamp());
  }

  private void revertLastChange(int... diffsIndices) throws IOException {
    revertChange(0, diffsIndices);
  }

  private void revertChange(int change, int... diffsIndices) throws IOException {
    List<Revision> revs = getVcsRevisionsFor(root);
    Revision leftRev = revs.get(change + 1);
    Revision rightRev = revs.get(change);
    List<Difference> diffs = leftRev.getDifferencesWith(rightRev);
    List<Difference> toRevert = new ArrayList<Difference>();
    for (int i : diffsIndices) {
      toRevert.add(diffs.get(i));
    }
    if (diffsIndices.length == 0) toRevert = diffs;
    new DifferenceReverter(getVcs(), gateway, toRevert, leftRev).revert();
  }
}
