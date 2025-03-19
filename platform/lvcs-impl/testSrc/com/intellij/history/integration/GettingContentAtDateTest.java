// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.integration;

import com.intellij.history.FileRevisionTimestampComparator;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.vfs.VirtualFile;

import java.nio.charset.StandardCharsets;

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
}