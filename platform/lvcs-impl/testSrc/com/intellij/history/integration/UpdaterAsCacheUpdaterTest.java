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

package com.intellij.history.integration;

import com.intellij.ide.startup.FileContent;
import com.intellij.history.core.InMemoryLocalVcs;
import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.LocalVcsTestCase;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Before;
import org.junit.Test;

public class UpdaterAsCacheUpdaterTest extends LocalVcsTestCase {
  LocalVcs vcs = new InMemoryLocalVcs();
  Updater updater;

  TestVirtualFile root;
  TestVirtualFile file;

  @Before
  public void setUp() {
    root = new TestVirtualFile("root");
    file = new TestVirtualFile("file", "new content", 1L);
    root.addChild(file);

    TestIdeaGateway gw = new TestIdeaGateway();
    gw.setContentRoots(root);
    updater = new Updater(vcs, gw);
  }

  @Test
  public void testCreatingNewFiles() {
    VirtualFile[] files = updater.queryNeededFiles();
    assertEquals(1, files.length);
    assertSame(file, files[0]);

    updater.processFile(fileContentOf(file));
    updater.updatingDone();

    assertEquals(c("new content"), vcs.getEntry("root/file").getContent());
  }

  @Test
  public void testUpdaingOutdatedFiles() {
    vcs.createDirectory("root");
    vcs.createFile("root/file", cf("old content"), file.getTimeStamp() - 1, false);

    VirtualFile[] files = updater.queryNeededFiles();
    assertEquals(1, files.length);
    assertSame(file, files[0]);

    updater.processFile(fileContentOf(file));
    updater.updatingDone();
    assertEquals(c("new content"), vcs.getEntry("root/file").getContent());
  }

  @Test
  public void testCreatingNewFilesOnlyOnProcessingFile() {
    updater.queryNeededFiles();
    assertFalse(vcs.hasEntry("root/file"));

    updater.processFile(fileContentOf(file));
    assertTrue(vcs.hasEntry("root/file"));
    assertEquals(c("new content"), vcs.getEntry("root/file").getContent());
  }

  @Test
  public void testUpdaingOutdatedFilesOnlyOnProcessingFile() {
    vcs.createDirectory("root");
    vcs.createFile("root/file", cf("old content"), file.getTimeStamp() - 1, false);

    updater.queryNeededFiles();
    assertEquals(c("old content"), vcs.getEntry("root/file").getContent());

    updater.processFile(fileContentOf(file));
    assertEquals(c("new content"), vcs.getEntry("root/file").getContent());
  }

  private FileContent fileContentOf(VirtualFile f) {
    return CacheUpdaterHelper.fileContentOf(f);
  }
}
