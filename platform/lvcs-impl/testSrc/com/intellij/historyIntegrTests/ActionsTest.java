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


import com.intellij.history.core.revisions.Revision;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.history.utils.RunnableAdapter;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.List;

public class ActionsTest extends IntegrationTestCase {
  public void testSavingDocumentBeforeAndAfterAction() throws Exception {
    VirtualFile f = createFile("f.txt", "file1");
    setContent(f, "file2");

    setDocumentTextFor(f, "doc1");
    LocalHistoryAction a = LocalHistory.getInstance().startAction("name");

    setDocumentTextFor(f, "doc2");
    a.finish();

    List<Revision> rr = getRevisionsFor(f);
    assertEquals(5, rr.size());
    assertContent("doc2", rr.get(0).findEntry());
    assertEquals("name", rr.get(1).getChangeSetName());
    assertContent("doc1", rr.get(1).findEntry());
    assertContent("file2", rr.get(2).findEntry());
    assertContent("file1", rr.get(3).findEntry());
  }

  public void testActionInsideCommand() throws Exception {
    // This is very important test. Mostly all actions are performed
    // inside surrounding command. Therefore we have to correctly
    // handle such situation.
    final VirtualFile f = createFile("f.txt");
    setContent(f, "file");
    setDocumentTextFor(f, "doc1");

    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        LocalHistoryAction a = LocalHistory.getInstance().startAction("action");
        setDocumentTextFor(f, "doc2");
        a.finish();
      }
    }, "command", null);

    List<Revision> rr = getRevisionsFor(f);
    assertEquals(5, rr.size());
    assertContent("doc2", rr.get(0).findEntry());
    assertEquals("command", rr.get(1).getChangeSetName());
    assertContent("doc1", rr.get(1).findEntry());
    assertContent("file", rr.get(2).findEntry());
    assertContent("", rr.get(3).findEntry());
  }

  public void testActionInsideCommandSurroundedWithSomeChanges() throws Exception {
    // see testActionInsideCommand comment
    final VirtualFile f = createFile("f.txt");

    CommandProcessor.getInstance().executeCommand(myProject, new RunnableAdapter() {
      @Override
      public void doRun() throws IOException {
        setContent(f, "file");
        setDocumentTextFor(f, "doc1");

        LocalHistoryAction a = LocalHistory.getInstance().startAction("action");
        setDocumentTextFor(f, "doc2");
        a.finish();

        saveDocument(f);
        setContent(f, "doc3");
      }
    }, "command", null);

    List<Revision> rr = getRevisionsFor(f);
    assertEquals(4, rr.size());

    assertContent("doc3", rr.get(0).findEntry());
    assertContent("doc1", rr.get(1).findEntry());
    assertContent("", rr.get(2).findEntry());

    assertEquals("command", rr.get(1).getChangeSetName());
    assertNull(rr.get(2).getChangeSetName());
  }

  private void saveDocument(VirtualFile f) {
    FileDocumentManager dm = FileDocumentManager.getInstance();
    dm.saveDocument(dm.getDocument(f));
  }
}