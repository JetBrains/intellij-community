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

package com.intellij.historyIntegrTests.ui;

import com.intellij.history.core.InMemoryLocalVcs;
import com.intellij.history.core.LocalVcs;
import com.intellij.history.integration.TestIdeaGateway;
import com.intellij.history.integration.TestVirtualFile;
import com.intellij.history.integration.ui.models.DirectoryHistoryDialogModel;
import com.intellij.history.integration.ui.views.DirectoryChange;
import com.intellij.openapi.vcs.changes.Change;

import java.util.List;

public class DirectoryHistoryDialogModelTest extends LocalHistoryUITestCase {
  private final LocalVcs vcs = new InMemoryLocalVcs();
  private DirectoryHistoryDialogModel m;

  public void testTitle() {
    TestVirtualFile parent = new TestVirtualFile("parent", null, -1);
    TestVirtualFile file = new TestVirtualFile("file", null, -1);
    parent.addChild(file);

    m = new DirectoryHistoryDialogModel(null, vcs, file);

    assertEquals("parent/file", m.getTitle());
  }

  public void testNoDifference() {
    vcs.createDirectory("dir");
    initModelFor("dir");

    assertEquals(1, m.getRevisions().size());

    m.selectRevisions(0, 0);
    assertTrue(m.getChanges().isEmpty());
  }

  public void testDifference() {
    vcs.createDirectory("dir");
    long timestamp = -1;
    vcs.createFile("dir/file", null, timestamp, false);

    initModelFor("dir");

    assertEquals(2, m.getRevisions().size());

    m.selectRevisions(0, 1);
    List<Change> cc = m.getChanges();
    assertEquals(1, cc.size());
    assertEquals("file", ((DirectoryChange)cc.get(0)).getModel().getEntryName(1));
  }

  private void initModelFor(String path) {
    m = new DirectoryHistoryDialogModel(new TestIdeaGateway(), vcs, new TestVirtualFile(path));
  }
}
