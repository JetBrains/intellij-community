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
package com.intellij.application.options;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;
import com.intellij.openapi.editor.TextChange;
import com.intellij.openapi.editor.impl.softwrap.TextChangeImpl;
import com.intellij.openapi.util.TextRange;
import org.junit.Test;
import org.junit.Before;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 10/14/2010
 */
public class ChangesDiffCalculatorTest {

  private ChangesDiffCalculator myCalculator;  

  @Before
  public void setUp() {
    myCalculator = new ChangesDiffCalculator();
  }

  @Test
  public void singleRemoveBefore() {
    doTest(asList(new TextChangeImpl("as", 1, 1)), Collections.<TextChange>emptyList(), new TextRange(1, 3));
  }

  @Test
  public void singleInsertBefore() {
    doTest(asList(new TextChangeImpl("", 1, 2)), Collections.<TextChange>emptyList(), new TextRange(1, 1));
  }

  @Test
  public void singleReplaceBefore() {
    doTest(asList(new TextChangeImpl("asd", 2, 3)), Collections.<TextChange>emptyList(), new TextRange(2, 5));
  }

  @Test
  public void singleRemoveAfter() {
    doTest(Collections.<TextChange>emptyList(), asList(new TextChangeImpl("as", 1, 1)), new TextRange(1, 1));
  }

  @Test
  public void singleInsertAfter() {
    doTest(Collections.<TextChange>emptyList(), asList(new TextChangeImpl("", 1, 2)), new TextRange(1, 2));
  }

  @Test
  public void singleReplaceAfter() {
    doTest(Collections.<TextChange>emptyList(), asList(new TextChangeImpl("a", 1, 2)), new TextRange(1, 2));
  }

  @Test
  public void scatteredRemoves() {
    doTest(new TextChangeImpl("1", 1, 1), new TextChangeImpl("4", 4, 4), new TextRange(1, 2), new TextRange(4, 4));
  }

  @Test
  public void scatteredInserts() {
    doTest(new TextChangeImpl("", 5, 6), new TextChangeImpl("", 1, 2), new TextRange(1, 2), new TextRange(6, 6));
  }

  @Test
  public void twoUnmatchedBeforeChangesAndMatchedAfterThat() {
    doTest(
      new ChangeListBuilder().add("01", 0, 0).add("56", 3, 4).add("", 5, 7).add("", 9, 12),
      new ChangeListBuilder().add("", 8, 10).add("", 12, 15),
      new TextRange(0, 2), new TextRange(5, 7)
    );
  }

  @Test
  public void unmatchedAfterChangeAndMatchedAfterThat() {
    doTest(
      new ChangeListBuilder().add(562, 566).add(570, 586).add(595, 597),
      new ChangeListBuilder().add(540, 548).add(570, 574).add(578, 594).add(603, 605),
      new TextRange(540, 548)
    );
  }

  @Test
  public void currentCoversBeforeTail() {
    doTest(
      new ChangeListBuilder().add("1234", 1, 3),
      new ChangeListBuilder().add("234567", 2, 5),
      new TextRange(1, 5)
    );
  }

  @Test
  public void currentCoversBeforeContent() {
    doTest(
      new ChangeListBuilder().add("1234567", 1, 3),
      new ChangeListBuilder().add("234", 2, 5),
      new TextRange(1, 5)
    );
  }

  @Test
  public void sameStartDifferentEnd() {
    doTest(
      new ChangeListBuilder().add(313, 314).add(318, 320).add(342, 344).add(372, 375).add(595, 597),
      new ChangeListBuilder().add(313, 317).add(321, 323).add(345, 347).add(375, 378).add(598, 600).add(603, 607),
      new TextRange(313, 317), new TextRange(603, 607)
    );
  }

  @Test
  public void unmatchedInsertsBefore() {
    doTest(
      new ChangeListBuilder().add(31, 35).add(37, 41).add(55, 59).add(61, 65),
      new ChangeListBuilder().add(31, 32).add(48, 49),
      new TextRange(31, 32), new TextRange(34, 34), new TextRange(48, 49), new TextRange(51, 51)
    );
  }

  private void doTest(TextChange beforeChange, TextChange afterChange, TextRange... expected) {
    doTest(asList(beforeChange), asList(afterChange), expected);
  }

  private void doTest(ChangeListBuilder beforeChanges, ChangeListBuilder currentChanges, TextRange... expected) {
    doTest(beforeChanges.changes, currentChanges.changes, expected);
  }

  private void doTest(List<? extends TextChange> beforeChanges, List<? extends TextChange> currentChanges, TextRange... expected) {
    Collection<TextRange> actual = myCalculator.calculateDiff(beforeChanges, currentChanges);
    assertEquals(expected.length, actual.size());
    int i = 0;
    for (TextRange range : actual) {
      assertEquals(expected[i++], range);
    }
  }

  private static class ChangeListBuilder {

   final List<TextChange> changes = new ArrayList<TextChange>();

    ChangeListBuilder add(int startOffset, int endOffset) {
      return add("", startOffset, endOffset);
    }

    ChangeListBuilder add(String text, int startOffset, int endOffset) {
      return add(new TextChangeImpl(text, startOffset, endOffset));
    }

    ChangeListBuilder add(TextChange change) {
      changes.add(change);
      return this;
    }
  }
}
