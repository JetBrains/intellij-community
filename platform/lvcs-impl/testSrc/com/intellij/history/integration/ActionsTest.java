// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.utils.RunnableAdapter;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ActionsTest extends IntegrationTestCase {
  public void testSavingDocumentBeforeAndAfterAction() throws Exception {
    VirtualFile f = createFile("f.txt", "file1");
    loadContent(f);
    setContent(f, "file2");

    setDocumentTextFor(f, "doc1");

    LocalHistoryAction a = LocalHistory.getInstance().startAction("action");
    setDocumentTextFor(f, "doc2");
    a.finish();

    List<ChangeSet> changes = getChangesFor(f);
    assertEquals("""
                   action: doc1
                   null: file2
                   null: file1
                   External change: null""", getNameAndOldContent(changes, f));
  }

  /**
   * <strong>This is very important test.</strong>
   * Almost all actions are performed inside surrounding command.
   * Therefore we have to correctly handle such situations.
   */
  public void testActionInsideCommand() throws Exception {
    final VirtualFile f = createFile("f.txt");
    setContent(f, "initial");
    setDocumentTextFor(f, "doc1");

    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      LocalHistoryAction a = LocalHistory.getInstance().startAction("action");
      setDocumentTextFor(f, "doc2");
      a.finish();
    }, "command", null);

    List<ChangeSet> changes = getChangesFor(f);
    assertEquals("""
                   command: doc1
                   null: initial
                   null:\s
                   External change: null""", getNameAndOldContent(changes, f));
  }

  public void testActionInsideCommandSurroundedWithSomeChanges() throws Exception {
    // see testActionInsideCommand comment
    final VirtualFile f = createFile("f.txt");

    CommandProcessor.getInstance().executeCommand(myProject, new RunnableAdapter() {
      @Override
      public void doRun() {
        setContent(f, "file");
        setDocumentTextFor(f, "doc1");

        LocalHistoryAction a = LocalHistory.getInstance().startAction("action");
        setDocumentTextFor(f, "doc2");
        a.finish();

        saveDocument(f);
        setContent(f, "doc3");
      }
    }, "command", null);

    List<ChangeSet> changes = getChangesFor(f);
    assertEquals("""
                   command: doc1
                   null:\s
                   External change: null""", getNameAndOldContent(changes, f));
  }

  private @NotNull String getNameAndOldContent(@NotNull List<ChangeSet> changes, @NotNull VirtualFile file) {
    return StringUtil.join(changes, change -> {
      String name = change.getName();
      Entry entry = getEntryFor(change, file);
      String content = entry == null ? "null" : getContentAsString(entry);
      return name + ": " + content;
    }, "\n");
  }

  private static void saveDocument(VirtualFile f) {
    FileDocumentManager dm = FileDocumentManager.getInstance();
    Document document = dm.getDocument(f);
    assertNotNull(f.getPath(), document);
    dm.saveDocument(document);
  }
}