/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.history.Clock;
import com.intellij.history.LocalHistory;
import com.intellij.history.core.LocalHistoryTestCase;
import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.core.revisions.Revision;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class PurgingTest extends IntegrationTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    getVcs().getChangeListInTests().setIntervalBetweenActivities(2);
  }

  @Test
  public void testPurgeWithoutGapsBetweenChanges() {
    createChangesWithTimestamps(1, 2, 3);
    getVcs().getChangeListInTests().purgeObsolete(2);
    assertRemainedChangesTimestamps(3, 2);
  }

  @Test
  public void testPurgeNothing() {
    createChangesWithTimestamps(1, 2, 3);
    getVcs().getChangeListInTests().purgeObsolete(10);
    assertRemainedChangesTimestamps(3, 2, 1);
  }

  @Test
  public void testDoesNotPurgeTheOnlyChange() {
    createChangesWithTimestamps(1);
    getVcs().getChangeListInTests().purgeObsolete(1);
    assertRemainedChangesTimestamps(1);
  }

  @Test
  public void testPurgeWithOneGap() {
    createChangesWithTimestamps(1, 2, 4);
    getVcs().getChangeListInTests().purgeObsolete(2);
    assertRemainedChangesTimestamps(4, 2);
  }

  @Test
  public void testPurgeWithSeveralGaps() {
    createChangesWithTimestamps(1, 2, 4, 5, 7, 8);
    getVcs().getChangeListInTests().purgeObsolete(5);
    assertRemainedChangesTimestamps(8, 7, 5, 4, 2);
  }

  @Test
  public void testPurgeWithLongGaps() {
    createChangesWithTimestamps(10, 20, 30, 40);
    getVcs().getChangeListInTests().purgeObsolete(3);
    assertRemainedChangesTimestamps(40, 30, 20);
  }

  @Test
  public void testPurgeWithBifIntervalBetweenChanges() {
    getVcs().getChangeListInTests().setIntervalBetweenActivities(100);

    createChangesWithTimestamps(110, 120, 130, 250, 260, 270);
    getVcs().getChangeListInTests().purgeObsolete(40);
    assertRemainedChangesTimestamps(270, 260, 250, 130, 120);
  }

  @Test
  public void testPurgingEmptyListDoesNotThrowException() {
    getVcs().getChangeListInTests().purgeObsolete(50);
  }

  @Test
  public void testChangesAfterPurge() throws IOException {
    Clock.setCurrentTimestamp(1);
    VirtualFile f = createFile("file.txt");
    Clock.setCurrentTimestamp(2);
    setContent(f, "1");
    Clock.setCurrentTimestamp(3);
    setContent(f, "2");

    assertEquals(3, LocalHistoryTestCase.collectChanges(getVcs(), f.getPath(), myProject.getLocationHash(), null).size());

    getVcs().getChangeListInTests().purgeObsolete(2);

    assertEquals(2, LocalHistoryTestCase.collectChanges(getVcs(), f.getPath(), myProject.getLocationHash(), null).size());
  }

  @Test
  public void testLabelsAfterPurge() throws IOException {
    Clock.setCurrentTimestamp(1);
    VirtualFile file = createFile("file");

    Clock.setCurrentTimestamp(2);
    LocalHistory.getInstance().putUserLabel(myProject, "1");

    getVcs().getChangeListInTests().purgeObsolete(1);

    List<Revision> rr = getRevisionsFor(file);
    assertEquals(2, rr.size());
    assertEquals("1", rr.get(1).getLabel());
  }

  private void createChangesWithTimestamps(long... tt) {
    for (long t : tt) {
      Clock.setCurrentTimestamp(t);
      getVcs().beginChangeSet();
      getVcs().putUserLabel("foo", "project");
      getVcs().endChangeSet(null);
    }
  }

  private void assertRemainedChangesTimestamps(long... tt) {
    assertEquals(tt.length, getVcs().getChangeListInTests().getChangesInTests().size());
    for (int i = 0; i < tt.length; i++) {
      long t = tt[i];
      ChangeSet c = getVcs().getChangeListInTests().getChangesInTests().get(i);
      assertEquals(t, c.getTimestamp());
    }
  }
}
