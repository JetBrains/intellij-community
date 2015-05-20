/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.comparison;

import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"InstanceofCatchParameter"})
public class ComparisonUtilAutoTest extends AutoTestCase {
  private static DumbProgressIndicator INDICATOR = DumbProgressIndicator.INSTANCE;
  private static ComparisonManager myComparisonManager = new ComparisonManagerImpl();

  private static final int CHAR_COUNT = 12;
  private static final Map<Integer, Character> CHAR_TABLE = initCharMap();

  private boolean myOldRegistryValue;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myOldRegistryValue = Registry.get("diff.verify.iterable").asBoolean();
    Registry.get("diff.verify.iterable").setValue(true);
  }

  @Override
  protected void tearDown() throws Exception {
    Registry.get("diff.verify.iterable").setValue(myOldRegistryValue);
    super.tearDown();
  }

  public void testChar() throws Exception {
    doTestChar(System.currentTimeMillis(), 30, 30);
  }

  public void testLine() throws Exception {
    doTestLine(System.currentTimeMillis(), 30, 300);
  }

  public void testLineSquashed() throws Exception {
    doTestLineSquashed(System.currentTimeMillis(), 30, 300);
  }

  public void testLineTrimSquashed() throws Exception {
    doTestLineTrimSquashed(System.currentTimeMillis(), 30, 300);
  }

  private void doTestLine(long seed, int runs, int maxLength) throws Exception {
    ComparisonPolicy[] policies = {ComparisonPolicy.DEFAULT, ComparisonPolicy.TRIM_WHITESPACES, ComparisonPolicy.IGNORE_WHITESPACES};

    doTest(seed, runs, maxLength, policies, new TestTask() {
      @Override
      public void run(@NotNull Document text1, @NotNull Document text2, @NotNull ComparisonPolicy policy, @NotNull Ref<Object> debugData) {
        CharSequence sequence1 = text1.getCharsSequence();
        CharSequence sequence2 = text2.getCharsSequence();

        List<LineFragment> fragments = myComparisonManager.compareLinesInner(sequence1, sequence2, policy, INDICATOR);
        debugData.set(fragments);

        checkResultLine(text1, text2, fragments, policy, true);
      }
    });
  }

  private void doTestLineSquashed(long seed, int runs, int maxLength) throws Exception {
    ComparisonPolicy[] policies = {ComparisonPolicy.DEFAULT, ComparisonPolicy.TRIM_WHITESPACES, ComparisonPolicy.IGNORE_WHITESPACES};

    doTest(seed, runs, maxLength, policies, new TestTask() {
      @Override
      public void run(@NotNull Document text1, @NotNull Document text2, @NotNull ComparisonPolicy policy, @NotNull Ref<Object> debugData) {
        CharSequence sequence1 = text1.getCharsSequence();
        CharSequence sequence2 = text2.getCharsSequence();

        List<LineFragment> fragments = myComparisonManager.compareLinesInner(sequence1, sequence2, policy, INDICATOR);
        debugData.set(fragments);

        List<LineFragment> squashedFragments = myComparisonManager.squash(fragments);
        debugData.set(new Object[]{fragments, squashedFragments});

        checkResultLine(text1, text2, squashedFragments, policy, false);
      }
    });
  }

  private void doTestLineTrimSquashed(long seed, int runs, int maxLength) throws Exception {
    ComparisonPolicy[] policies = {ComparisonPolicy.DEFAULT, ComparisonPolicy.TRIM_WHITESPACES, ComparisonPolicy.IGNORE_WHITESPACES};

    doTest(seed, runs, maxLength, policies, new TestTask() {
      @Override
      public void run(@NotNull Document text1, @NotNull Document text2, @NotNull ComparisonPolicy policy, @NotNull Ref<Object> debugData) {
        CharSequence sequence1 = text1.getCharsSequence();
        CharSequence sequence2 = text2.getCharsSequence();

        List<LineFragment> fragments = myComparisonManager.compareLinesInner(sequence1, sequence2, policy, INDICATOR);
        debugData.set(fragments);

        List<LineFragment> processed = myComparisonManager.processBlocks(fragments, sequence1, sequence2, policy, true, true);
        debugData.set(new Object[]{fragments, processed});

        checkResultLine(text1, text2, processed, policy, false);
      }
    });
  }

  private void doTestChar(long seed, int runs, int maxLength) throws Exception {
    ComparisonPolicy[] policies = {ComparisonPolicy.DEFAULT, ComparisonPolicy.IGNORE_WHITESPACES};

    doTest(seed, runs, maxLength, policies, new TestTask() {
      @Override
      public void run(@NotNull Document text1, @NotNull Document text2, @NotNull ComparisonPolicy policy, @NotNull Ref<Object> debugData) {
        CharSequence sequence1 = text1.getCharsSequence();
        CharSequence sequence2 = text2.getCharsSequence();

        List<DiffFragment> fragments = myComparisonManager.compareChars(sequence1, sequence2, policy, INDICATOR);
        debugData.set(fragments);

        checkResultChar(sequence1, sequence2, fragments, policy);
      }
    });
  }

  private void doTest(long seed, int runs, int maxLength, @NotNull ComparisonPolicy[] policies, @NotNull TestTask test) throws Exception {
    myRng.setSeed(seed);

    ComparisonPolicy policy = null;
    Ref<Object> debugData = new Ref<Object>();

    for (int i = 0; i < runs; i++) {
      if (i % 1000 == 0) System.out.println(i);
      Document text1 = null;
      Document text2 = null;
      try {
        rememberSeed();

        text1 = generateText(maxLength);
        text2 = generateText(maxLength);

        for (ComparisonPolicy comparisonPolicy : policies) {
          policy = comparisonPolicy;
          test.run(text1, text2, comparisonPolicy, debugData);
        }
      }
      catch (Throwable e) {
        System.out.println("Seed: " + seed);
        System.out.println("Runs: " + runs);
        System.out.println("MaxLength: " + maxLength);
        System.out.println("Policy: " + policy);
        System.out.println("I: " + i);
        System.out.println("Current seed: " + getLastSeed());
        System.out.println("Text1: " + textToReadableFormat(text1));
        System.out.println("Text2: " + textToReadableFormat(text2));
        System.out.println("Debug Data: " + debugData.get());
        if (e instanceof Error) throw (Error)e;
        if (e instanceof Exception) throw (Exception)e;
        throw new Exception(e);
      }
    }
  }

  private static void checkResultLine(@NotNull Document text1, @NotNull Document text2,
                                      @NotNull List<LineFragment> fragments,
                                      @NotNull ComparisonPolicy policy,
                                      boolean allowNonSquashed) {
    checkLineConsistency(text1, text2, fragments, allowNonSquashed);

    for (LineFragment fragment : fragments) {
      if (fragment.getInnerFragments() != null) {
        CharSequence sequence1 = subsequence(text1, fragment.getStartOffset1(), fragment.getEndOffset1());
        CharSequence sequence2 = subsequence(text2, fragment.getStartOffset2(), fragment.getEndOffset2());

        checkResultWord(sequence1, sequence2, fragment.getInnerFragments(), policy);
      }
    }

    checkUnchanged(text1.getCharsSequence(), text2.getCharsSequence(), fragments, policy, true);
    checkCantTrimLines(text1, text2, fragments, policy, allowNonSquashed);
  }

  private static void checkResultWord(@NotNull CharSequence text1, @NotNull CharSequence text2,
                                      @NotNull List<DiffFragment> fragments,
                                      @NotNull ComparisonPolicy policy) {
    checkDiffConsistency(fragments);

    checkUnchanged(text1, text2, fragments, policy, false);
  }

  private static void checkResultChar(@NotNull CharSequence text1, @NotNull CharSequence text2,
                                      @NotNull List<DiffFragment> fragments,
                                      @NotNull ComparisonPolicy policy) {
    checkDiffConsistency(fragments);

    checkUnchanged(text1, text2, fragments, policy, false);
  }

  private static void checkLineConsistency(@NotNull Document text1, @NotNull Document text2,
                                           @NotNull List<LineFragment> fragments,
                                           boolean allowNonSquashed) {
    int last1 = -1;
    int last2 = -1;

    for (LineFragment fragment : fragments) {
      int startOffset1 = fragment.getStartOffset1();
      int startOffset2 = fragment.getStartOffset2();
      int endOffset1 = fragment.getEndOffset1();
      int endOffset2 = fragment.getEndOffset2();

      int start1 = fragment.getStartLine1();
      int start2 = fragment.getStartLine2();
      int end1 = fragment.getEndLine1();
      int end2 = fragment.getEndLine2();

      assertTrue(startOffset1 >= 0);
      assertTrue(startOffset2 >= 0);
      assertTrue(endOffset1 <= text1.getTextLength());
      assertTrue(endOffset2 <= text2.getTextLength());

      assertTrue(start1 >= 0);
      assertTrue(start2 >= 0);
      assertTrue(end1 <= getLineCount(text1));
      assertTrue(end2 <= getLineCount(text2));

      assertTrue(startOffset1 <= endOffset1);
      assertTrue(startOffset2 <= endOffset2);

      assertTrue(start1 <= end1);
      assertTrue(start2 <= end2);
      assertTrue(start1 != end1 || start2 != end2);

      assertTrue(allowNonSquashed || start1 != last1 || start2 != last2);

      checkLineOffsets(fragment, text1, text2);

      last1 = end1;
      last2 = end2;
    }
  }

  private static void checkDiffConsistency(@NotNull List<? extends DiffFragment> fragments) {
    int last1 = -1;
    int last2 = -1;

    for (DiffFragment diffFragment : fragments) {
      int start1 = diffFragment.getStartOffset1();
      int start2 = diffFragment.getStartOffset2();
      int end1 = diffFragment.getEndOffset1();
      int end2 = diffFragment.getEndOffset2();

      assertTrue(start1 <= end1);
      assertTrue(start2 <= end2);
      assertTrue(start1 != end1 || start2 != end2);

      assertTrue(start1 != last1 || start2 != last2);

      last1 = end1;
      last2 = end2;
    }
  }

  private static void checkLineOffsets(@NotNull LineFragment fragment, @NotNull Document before, @NotNull Document after) {
    checkLineOffsets(before, fragment.getStartLine1(), fragment.getEndLine1(),
                     fragment.getStartOffset1(), fragment.getEndOffset1());

    checkLineOffsets(after, fragment.getStartLine2(), fragment.getEndLine2(),
                     fragment.getStartOffset2(), fragment.getEndOffset2());
  }

  private static void checkLineOffsets(@NotNull Document document,
                                       int startLine, int endLine,
                                       int startOffset, int endOffset) {
    if (startLine != endLine) {
      assertEquals(document.getLineStartOffset(startLine), startOffset);
      int offset = document.getLineEndOffset(endLine - 1);
      if (offset < document.getTextLength()) offset++;
      assertEquals(offset, endOffset);
    }
    else {
      int offset = startLine == getLineCount(document)
                   ? document.getTextLength()
                   : document.getLineStartOffset(startLine);
      assertEquals(offset, startOffset);
      assertEquals(offset, endOffset);
    }
  }

  private static void checkUnchanged(@NotNull CharSequence text1, @NotNull CharSequence text2,
                                     @NotNull List<? extends DiffFragment> fragments,
                                     @NotNull ComparisonPolicy policy,
                                     boolean skipNewline) {
    // TODO: better check for Trim spaces case ?
    boolean ignoreSpaces = policy != ComparisonPolicy.DEFAULT;

    int last1 = 0;
    int last2 = 0;
    for (DiffFragment fragment : fragments) {
      CharSequence chunk1 = text1.subSequence(last1, fragment.getStartOffset1());
      CharSequence chunk2 = text2.subSequence(last2, fragment.getStartOffset2());

      assertEqualsCharSequences(chunk1, chunk2, ignoreSpaces, skipNewline);

      last1 = fragment.getEndOffset1();
      last2 = fragment.getEndOffset2();
    }
    CharSequence chunk1 = text1.subSequence(last1, text1.length());
    CharSequence chunk2 = text2.subSequence(last2, text2.length());
    assertEqualsCharSequences(chunk1, chunk2, ignoreSpaces, skipNewline);
  }

  private static void checkCantTrimLines(@NotNull Document text1, @NotNull Document text2,
                                         @NotNull List<? extends LineFragment> fragments,
                                         @NotNull ComparisonPolicy policy, boolean allowNonSquashed) {
    for (LineFragment fragment : fragments) {
      Couple<CharSequence> sequence1 = getFirstLastLines(text1, fragment.getStartLine1(), fragment.getEndLine1());
      Couple<CharSequence> sequence2 = getFirstLastLines(text2, fragment.getStartLine2(), fragment.getEndLine2());
      if (sequence1 == null || sequence2 == null) continue;

      checkNonEqualsIfLongEnough(sequence1.first, sequence2.first, policy, allowNonSquashed);
      checkNonEqualsIfLongEnough(sequence1.second, sequence2.second, policy, allowNonSquashed);
    }
  }

  private static void checkNonEqualsIfLongEnough(@NotNull CharSequence line1, @NotNull CharSequence line2,
                                                 @NotNull ComparisonPolicy policy, boolean allowNonSquashed) {
    // in non-squashed blocks non-trimmed elements are possible, if it's 'unimportant' lines
    if (allowNonSquashed && countNonWhitespaceCharacters(line1) <= Registry.get("diff.unimportant.line.char.count").asInteger()) return;
    if (allowNonSquashed && countNonWhitespaceCharacters(line2) <= Registry.get("diff.unimportant.line.char.count").asInteger()) return;

    assertFalse(myComparisonManager.isEquals(line1, line2, policy));
  }

  private static int countNonWhitespaceCharacters(@NotNull CharSequence line) {
    int count = 0;
    for (int i = 0; i < line.length(); i++) {
      if (!StringUtil.isWhiteSpace(line.charAt(i))) count++;
    }
    return count;
  }

  @Nullable
  private static Couple<CharSequence> getFirstLastLines(@NotNull Document text, int start, int end) {
    if (start == end) return null;

    TextRange firstLineRange = DiffUtil.getLinesRange(text, start, start + 1);
    TextRange lastLineRange = DiffUtil.getLinesRange(text, end - 1, end);

    CharSequence firstLine = firstLineRange.subSequence(text.getCharsSequence());
    CharSequence lastLine = lastLineRange.subSequence(text.getCharsSequence());

    return Couple.of(firstLine, lastLine);
  }

  @NotNull
  private Document generateText(int maxLength) {
    return new DocumentImpl(generateText(maxLength, CHAR_COUNT, CHAR_TABLE));
  }

  @NotNull
  private static Map<Integer, Character> initCharMap() {
    Map<Integer, Character> map = new HashMap<Integer, Character>();

    List<Character> characters = new ArrayList<Character>();
    characters.add('\n');
    characters.add('\n');
    characters.add('\t');
    characters.add(' ');
    characters.add(' ');
    characters.add('.');
    characters.add('<');
    characters.add('!');

    for (int i = 0; i < characters.size(); i++) {
      map.put(i, characters.get(i));
    }

    return map;
  }

  @NotNull
  private static CharSequence subsequence(@NotNull Document document, int start, int end) {
    return document.getCharsSequence().subSequence(start, end);
  }

  private static int getLineCount(@NotNull Document document) {
    return Math.max(1, document.getLineCount());
  }

  private interface TestTask {
    void run(@NotNull Document text1, @NotNull Document text2, @NotNull ComparisonPolicy policy, @NotNull Ref<Object> debugData);
  }
}
