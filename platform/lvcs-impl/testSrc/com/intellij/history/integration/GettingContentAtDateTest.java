// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.integration;

import com.intellij.history.FileRevisionTimestampComparator;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.history.core.changes.Change;
import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.core.changes.ChangeVisitor;
import com.intellij.history.core.changes.ContentChange;
import com.intellij.history.integration.revertion.UndoChangeRevertingVisitor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class GettingContentAtDateTest extends IntegrationTestCase {
  private VirtualFile f;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    f = createChildData(myRoot, "f.txt");
  }

  public void testContentAtDate() {
    setContent(f, "1", TIMESTAMP_INCREMENT);
    setContent(f, "2", TIMESTAMP_INCREMENT * 2);

    assertContentAt(0, null);
    assertContentAt(TIMESTAMP_INCREMENT, "1");
    assertContentAt(TIMESTAMP_INCREMENT + TIMESTAMP_INCREMENT / 2, null);
    assertContentAt(TIMESTAMP_INCREMENT * 2, "2");
    assertContentAt(TIMESTAMP_INCREMENT * 3, null);
  }

  public void testContentAtDateForFilteredFilesIsNull() {
    VirtualFile f = createChildData(myRoot, "f.class");
    setContent(f, "1", 1111);

    assertContentAt(1111, null);
  }

  public void testGettingFirstAvailableContentAfterPurge() {
    Clock.setTime(1);
    setContent(f, "1", TIMESTAMP_INCREMENT);
    Clock.setTime(2);
    setContent(f, "2", TIMESTAMP_INCREMENT * 2);
    Clock.setTime(3);
    setContent(f, "3", TIMESTAMP_INCREMENT * 3);

    getVcs().getChangeListInTests().purgeObsolete(2);

    assertContentAt(TIMESTAMP_INCREMENT, null);
    assertContentAt(TIMESTAMP_INCREMENT * 2, "2");
    assertContentAt(TIMESTAMP_INCREMENT * 3, "3");
  }

  public void testGettingMostRecentRevisionContent() {
    setContent(f, "1", TIMESTAMP_INCREMENT);
    setContent(f, "2", TIMESTAMP_INCREMENT * 2);

    FileRevisionTimestampComparator c = new FileRevisionTimestampComparator() {
      @Override
      public boolean isSuitable(long revisionTimestamp) {
        return revisionTimestamp < 10000;
      }
    };
    assertContentAt(c, "2");
  }

  public void testWithUnsavedDocuments() {
    setContent(f, "FILE1", TIMESTAMP_INCREMENT);

    Clock.setTime(TIMESTAMP_INCREMENT * 2);
    LocalHistoryAction a = LocalHistory.getInstance().startAction(null);
    setDocumentTextFor(f, "DOC1");
    a.finish();

    Clock.setTime(TIMESTAMP_INCREMENT * 3);
    a = LocalHistory.getInstance().startAction(null);
    setDocumentTextFor(f, "DOC2");
    a.finish();

    FileDocumentManager.getInstance().saveAllDocuments();
    setContent(f, "FILE2", TIMESTAMP_INCREMENT * 4);

    assertFileHistory("""
                        null 9000 9000 DOC2
                        null 9000 6000 DOC1
                        null 6000 3000 FILE1
                        """, TIMESTAMP_INCREMENT, TIMESTAMP_INCREMENT * 4);

    assertContentAt(new FileRevisionTimestampComparator() {
      @Override
      public boolean isSuitable(long revisionTimestamp) {
        return revisionTimestamp == TIMESTAMP_INCREMENT * 4;
      }
    }, "FILE2");

    assertContentAt(new FileRevisionTimestampComparator() {
      @Override
      public boolean isSuitable(long revisionTimestamp) {
        return revisionTimestamp == TIMESTAMP_INCREMENT * 3;
      }
    }, "DOC2");

    assertContentAt(new FileRevisionTimestampComparator() {
      @Override
      public boolean isSuitable(long revisionTimestamp) {
        return revisionTimestamp == TIMESTAMP_INCREMENT * 2;
      }
    }, "DOC1");

    assertContentAt(new FileRevisionTimestampComparator() {
      @Override
      public boolean isSuitable(long revisionTimestamp) {
        return revisionTimestamp == TIMESTAMP_INCREMENT;
      }
    }, "FILE1");
  }

  private void assertContentAt(long timestamp, String expected) {
    assertContentAt(comparator(timestamp), expected);
  }

  private void assertContentAt(FileRevisionTimestampComparator c, String expected) {
    byte[] actual = LocalHistory.getInstance().getByteContent(f, c);
    assertEquals(expected, actual == null ? null : new String(actual, StandardCharsets.UTF_8));
  }

  private FileRevisionTimestampComparator comparator(final long timestamp) {
    return new FileRevisionTimestampComparator() {
      @Override
      public boolean isSuitable(long revisionTimestamp) {
        return revisionTimestamp == timestamp;
      }
    };
  }

  private void assertFileHistory(String expected, int min, int max) {
    var actual = collectFileChanges(min, max)
      .stream()
      .map(c -> {
        return c.first.getName() + " " + c.first.getTimestamp() + " " + c.second.getOldTimestamp() + " " + c.second.getOldContent();
      }).collect(Collectors.joining("\n"));
    assertEquals(expected.trim(), actual);
  }

  private @NotNull ArrayList<Pair<ChangeSet, ContentChange>> collectFileChanges(int min, int max) {
    var filePath = f.getPath();
    var changes = new ArrayList<Pair<ChangeSet, ContentChange>>();
    LocalHistoryImpl.getInstanceImpl().getFacade().accept(new ChangeVisitor() {
      @Override
      public void begin(ChangeSet c) {
        if (c.anyChangeMatches(change -> change.affectsPath(filePath))) {
          for (Change change : c.getChanges()) {
            if (change instanceof ContentChange) {
              var t = ((ContentChange)change).getOldTimestamp();
              if (t >= min && t <= max) {
                changes.add(new Pair<>(c, (ContentChange)change));
              }
            }
          }
        }
      }
    });
    return changes;
  }
}