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
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.action.CustomAction;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import org.junit.Test;
import org.junit.Before;

import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 10/14/2010
 */
public class ChangesDiffCalculatorTest {

  private ChangesDiffCalculator myCalculator;
  private Mockery myMockery;
  private CharSequence myBeforeText;
  private CharSequence myCurrentText;
  private CharSequence myInitialText;

  @Before
  public void setUp() {
    myCalculator = new ChangesDiffCalculator();
    myMockery = new JUnit4Mockery() {{
      setImposteriser(ClassImposteriser.INSTANCE);
    }};

    myBeforeText = createText(1000, 0);
    myCurrentText = createText(1000, 10);
  }

  @After
  public void checkExpectations() {
    myMockery.assertIsSatisfied();
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
      new ChangeListBuilder().add("01", 0, 0).add("56", 3, 4).add(5, 7).add(9, 12),
      new ChangeListBuilder().add(8, 10).add(12, 15),
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
      new TextRange(314, 317), new TextRange(603, 607)
    );
  }

  @Test
  public void unmatchedInsertsBefore() {
    doTest(
      new ChangeListBuilder().add(31, 35).add(37, 41).add(55, 59).add(61, 65),
      new ChangeListBuilder().add(31, 32).add(48, 49),
      new TextRange(32, 32), new TextRange(34, 34), new TextRange(49, 49), new TextRange(51, 51)
    );
  }

  @Test
  public void doubleRemove() {
    doTest(
      new ChangeListBuilder().add(21, 23).add(49, 49).add(61, 97).add(105, 106),
      new ChangeListBuilder().add(21, 22).add(48, 48).add(60, 95).add(103, 104),
      new TextRange(22, 22), new TextRange(95, 95)
    );
  }

  @Test
  public void doubleInsert() {
    doTest(
      new ChangeListBuilder().add(21, 22).add(48, 48).add(60, 95).add(103, 104),
      new ChangeListBuilder().add(21, 23).add(49, 49).add(61, 97).add(105, 106),
      new TextRange(22, 23), new TextRange(96, 97)
    );
  }

  @Test
  public void singleChangeMapsTwoMultipleScatteredChanges() {
    String beforeText =
      "public class Foo {\n" +
      "   public void foo()\n" +
      "   {\n" +
      "      if (2 < 3) { return; }\n" +
      "      if (3 < 4) { return; }\n" +
      "   }\n" +
      "}";

    String currentText =
      "public class Foo {\n" +
      "  public void foo()\n" +
      "  {\n" +
      "    if (2 < 3) { return; }\n" +
      "    if (3 < 4) { return; }\n" +
      "  }\n" +
      "}";

    doTest(
      new ChangeListBuilder().add(21, 22).add(39, 42).add(49, 51).add("return;", 62, 73).add(78, 80).add("\n       return;", 90, 102)
        .add(105, 106), beforeText,
      new ChangeListBuilder().add(38, 40).add("return;", 58, 69).add("\n     ", 84, 84).add(" return;", 85, 96), currentText,
      new TextRange(21, 21), new TextRange(40, 40), new TextRange(47, 47), new TextRange(74, 74), new TextRange(99, 99)
    );
  }

  @Test
  public void changeIndentFromTwoToFour() {
    String beforeText =
      "public class Foo {\n" +
      "  public void foo()\n" +
      "  {\n" +
      "    int i;\n" +
      "label1:\n" +
      "    do {\n" +
      "    } while (true)\n" +
      "  }\n" +
      "}";

    String currentText =
      "public class Foo {\n" +
      "    public void foo()\n" +
      "    {\n" +
      "        int i;\n" +
      "label1:\n" +
      "        do {\n" +
      "        } while (true)\n" +
      "    }\n" +
      "}";

    doTest(
      new ChangeListBuilder().add(38, 40).add("  ", 47, 47).add("    ", 53, 54).add(61, 65).add(76, 77), beforeText,
      new ChangeListBuilder().add(21, 23).add(40, 44).add(53, 55).add("    ", 61, 62).add(69, 77).add(87, 91).add(92, 93).add(108, 110),
      currentText,
      new TextRange(21, 23), new TextRange(42, 44), new TextRange(51, 55), new TextRange(73, 77), new TextRange(87, 91),
      new TextRange(108, 110)
    );
  }

  @Test
  public void changeIndentFromOneToTwo() {
    String beforeText =
      "public class Foo {\n" +
      " public int[] X = new int[]{ 1, 3, 5\n" +
      "                                   7, 9, 11 };\n" +
      "\n" +
      " public void foo()\n" +
      " {\n" +
      " }\n" +
      "}";

    String currentText =
      "public class Foo {\n" +
      "  public int[] X = new int[]{ 1, 3, 5\n" +
      "                                    7, 9, 11 };\n" +
      "\n" +
      "  public void foo()\n" +
      "  {\n" +
      "  }\n" +
      "}";

    doTest(
      new ChangeListBuilder().add(" ", 20, 20).add(" ", 46, 46).add(58, 91).add(99, 100).add(" ", 103, 104).add(122, 123)
        .add(" ", 127, 127), beforeText,
      new ChangeListBuilder().add(" ", 47, 47).add(59, 93).add(101, 102).add(105, 106).add(125, 127), currentText,
      new TextRange(20, 21), new TextRange(92, 93), new TextRange(106, 107), new TextRange(126, 127), new TextRange(131, 132)
    );
  }

  @Test
  public void changeIndentFromOneToThree() {
    String beforeText =
      "public class Foo {\n" +
      " public int[] X = new int[]{ 1, 3, 5\n" +
      "                                   7, 9, 11 };\n" +
      "\n" +
      " public void foo()\n" +
      " {\n" +
      " }\n" +
      "}";

    String currentText =
      "public class Foo {\n" +
      "   public int[] X = new int[]{ 1, 3, 5\n" +
      "                                     7, 9, 11 };\n" +
      "\n" +
      "   public void foo()\n" +
      "   {\n" +
      "   }\n" +
      "}";

    doTest(
      new ChangeListBuilder().add(" ", 20, 20).add(" ", 46, 46).add(58, 91).add(99, 100).add(" ", 103, 104).add(122, 123)
        .add(" ", 127, 127), beforeText,
      new ChangeListBuilder().add(21, 22).add(" ", 48, 48).add(60, 95).add(103, 104).add(107, 109).add(128, 131).add(136, 137), currentText,
      new TextRange(20, 22), new TextRange(93, 95), new TextRange(108, 110), new TextRange(129, 131), new TextRange(135, 137)
    );
  }

  //@Test
  public void changeLabelIndentFromThreeToOne() {
    myInitialText =
      "public class Foo {\n" +
      "  public void foo() {\n" +
      "    label1: int i;\n" +
      "    if (true) {\n" +
      "      label2: int j;\n" +
      "    }\n" +
      "  }\n" +
      "}";

    String beforeText =
      "public class Foo {\n" +
      "    public void foo()\n" +
      "    {\n" +
      "   label1:\n" +
      "        int i;\n" +
      "        if (true) {\n" +
      "   label2:\n" +
      "            int j;\n" +
      "        }\n" +
      "    }\n" +
      "}";

    String currentText =
      "public class Foo {\n" +
      "    public void foo()\n" +
      "    {\n" +
      " label1:\n" +
      "        int i;\n" +
      "        if (true) {\n" +
      " label2:\n" +
      "            int j;\n" +
      "        }\n" +
      "    }\n" +
      "}";

    doTest(
      new ChangeListBuilder().add(21, 23).add(40, 44).add(" ", 50, 50).add(57, 65).add(77, 81).add("   ", 96, 96).add(103, 115)
        .add(127, 131).add(135, 137), beforeText,
      new ChangeListBuilder().add(21, 23).add(40, 44).add("   ", 48, 48).add(55, 63).add(75, 79).add("     ", 92, 92)
        .add(99, 111).add(123, 127).add(131, 133), currentText,
      new TextRange(48, 48), new TextRange(92, 92)
    );
  }

  private void doTest(TextChange beforeChange, TextChange afterChange, TextRange... expected) {
    doTest(asList(beforeChange), myBeforeText, asList(afterChange), myCurrentText, expected);
  }

  private void doTest(ChangeListBuilder beforeChanges, ChangeListBuilder currentChanges, TextRange... expected) {
    doTest(beforeChanges.changes, myBeforeText, currentChanges.changes, myCurrentText, expected);
  }

  private void doTest(ChangeListBuilder beforeChanges, CharSequence beforeText, ChangeListBuilder currentChanges,
                      CharSequence currentText, TextRange... expected)
  {
    doTest(beforeChanges.changes, beforeText, currentChanges.changes, currentText, expected);
  }

  private void doTest(List<? extends TextChange> beforeChanges, List<? extends TextChange> currentChanges, TextRange... expected) {
    doTest(beforeChanges, myBeforeText, currentChanges, myCurrentText, expected);
  }

  private void doTest(List<? extends TextChange> beforeChanges, CharSequence beforeText, List<? extends TextChange> currentChanges,
                      CharSequence currentText, TextRange... expected)
  {
    if (myInitialText != null) {
      checkChanges(beforeChanges, beforeText, myInitialText);
      checkChanges(currentChanges, currentText, myInitialText);
    }

    Collection<TextRange> actual = myCalculator.calculateDiff(beforeChanges, beforeText, currentChanges, currentText);
    assertEquals("expected " + Arrays.toString(expected) + ", actual " + actual, expected.length, actual.size());
    int i = 0;
    for (TextRange range : actual) {
      assertEquals(expected[i++], range);
    }
  }

  private static void checkChanges(List<? extends TextChange> changes, CharSequence textAfterChanges, CharSequence textBeforeChanges) {
    StringBuilder buffer = new StringBuilder(textAfterChanges);

    // Assuming here that given changes are sorted by start offset in ascending order.
    for (int i = changes.size() - 1; i >= 0; i--) {
      TextChange change = changes.get(i);
      buffer.replace(change.getStart(), change.getEnd(), change.getText().toString());
    }
    assertEquals(textBeforeChanges.toString(), buffer.toString());
  }

  private CharSequence createText(final int length, final int shift) {
    final CharSequence result = myMockery.mock(CharSequence.class);
    myMockery.checking(new Expectations() {{
      allowing(result).charAt(with(any(int.class))); will(new CustomAction("charAt()") {
        @Override
        public Object invoke(Invocation invocation) throws Throwable {
          Integer index = (Integer)invocation.getParameter(0);
          return (char)(index.intValue() + shift);
        }
      });
      allowing(result).length(); will(returnValue(length));
      allowing(result).subSequence(with(any(int.class)), with(any(int.class))); will(new CustomAction("subSequence") {
        @Override
        public Object invoke(Invocation invocation) throws Throwable {
          int start = (Integer)invocation.getParameter(0);
          int end = (Integer)invocation.getParameter(1);
          return createText(end - start, shift + start);
        }
      });
    }});
    return result;
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
