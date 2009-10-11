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

package com.intellij.history.core;

import com.intellij.history.core.storage.Storage;
import com.intellij.history.core.tree.Entry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

public class LocalVcsSavingTest extends TempDirTestCase {
  private LocalVcs vcs;
  private Storage s;

  @Before
  public void initVcs() {
    closeStorage();
    s = new Storage(tempDir);
    vcs = new LocalVcs(s);
  }

  @After
  public void closeStorage() {
    if (s != null) s.close();
  }

  @Test
  public void testSavingEntries() {
    vcs.createFile("file", cf("content"), 123L, false);

    vcs.save();
    initVcs();

    Entry e = vcs.findEntry("file");
    assertNotNull(e);
    assertEquals(c("content"), e.getContent());
    assertEquals(123L, e.getTimestamp());
  }

  @Test
  public void testSavingChangeList() {
    long timestamp = -1;
    vcs.createFile("file", cf("content"), timestamp, false);
    vcs.changeFileContent("file", cf("new content"), -1);

    vcs.save();
    initVcs();

    assertEquals(2, vcs.getRevisionsFor("file").size());
  }

  @Test
  public void testSavingObjectsCounter() {
    long timestamp1 = -1;
    vcs.createFile("file1", cf("content1"), timestamp1, false);
    long timestamp2 = -1;
    vcs.createFile("file2", cf("content2"), timestamp2, false);

    vcs.save();
    initVcs();

    long timestamp = -1;
    vcs.createFile("file3", cf("content3"), timestamp, false);

    int id2 = vcs.getEntry("file2").getId();
    int id3 = vcs.getEntry("file3").getId();

    assertTrue(id2 < id3);
  }

  @Test
  public void testSavingDuringChangeSet() {
    vcs.beginChangeSet();
    long timestamp = -1;
    vcs.createFile("file", cf(""), timestamp, false);

    vcs.save();
    initVcs();
    assertTrue(vcs.hasEntry("file"));
    assertEquals(1, vcs.getRevisionsFor("file").size());
  }
  
  @Test
  public void testDoesNotSaveIfNoChangesWereMade() {
    long timestamp = -1;
    vcs.createFile("f1", cf(""), timestamp, false);
    vcs.save();
    File f = new File(tempDir, "storage");
    f.setLastModified(1000);

    vcs.save();
    assertTrue(1000 == f.lastModified());

    long timestamp1 = -1;
    vcs.createFile("f2", cf(""), timestamp1, false);
    vcs.save();

    assertTrue(1000 != f.lastModified());
  }
  
  @Test
  public void testSavingAfterPurge() {
    vcs.createFile("f", cf("one"), 1, false);
    vcs.changeFileContent("f", cf("two"), 2);
    vcs.changeFileContent("f", cf("three"), 3);

    vcs.save();
    vcs.purgeObsoleteAndSave(0);

    initVcs();

    assertEquals(1, vcs.getRevisionsFor("f").size());
  }
}
