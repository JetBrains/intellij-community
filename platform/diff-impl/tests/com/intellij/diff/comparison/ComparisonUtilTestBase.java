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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.List;

public abstract class ComparisonUtilTestBase extends UsefulTestCase {
  private static DumbProgressIndicator INDICATOR = DumbProgressIndicator.INSTANCE;
  private static ComparisonManager myComparisonManager = new ComparisonManagerImpl();

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

  //
  // Impl
  //

  private static void doLineTest(@NotNull Document before,
                                 @NotNull Document after,
                                 @Nullable Couple<BitSet> matchings,
                                 @Nullable List<Change> expected,
                                 @NotNull ComparisonPolicy policy) {
    CharSequence sequence1 = before.getCharsSequence();
    CharSequence sequence2 = after.getCharsSequence();
    List<LineFragment> fragments = myComparisonManager.compareLines(sequence1, sequence2, policy, INDICATOR);
    checkConsistency(fragments, before, after, policy);
    if (matchings != null) checkLineMatching(fragments, before, after, matchings, policy);
    if (expected != null) checkLineChanges(fragments, before, after, expected, policy);
  }

  private static void doWordTest(@NotNull Document before,
                                 @NotNull Document after,
                                 @Nullable Couple<BitSet> matchings,
                                 @Nullable List<Change> expected,
                                 @NotNull ComparisonPolicy policy) {
    CharSequence sequence1 = before.getCharsSequence();
    CharSequence sequence2 = after.getCharsSequence();
    List<LineFragment> rawFragments = myComparisonManager.compareLinesInner(sequence1, sequence2, policy, INDICATOR);
    List<LineFragment> fragments = myComparisonManager.squash(rawFragments);

    checkConsistencyWord(fragments, before, after, policy);

    List<DiffFragment> diffFragments = fragments.get(0).getInnerFragments();
    assert diffFragments != null;

    if (matchings != null) checkDiffMatching(diffFragments, before, after, matchings, policy);
    if (expected != null) checkDiffChanges(diffFragments, before, after, expected, policy);
  }

  private static void doCharTest(@NotNull Document before,
                                 @NotNull Document after,
                                 @Nullable Couple<BitSet> matchings,
                                 @Nullable List<Change> expected,
                                 @NotNull ComparisonPolicy policy) {
    CharSequence sequence1 = before.getCharsSequence();
    CharSequence sequence2 = after.getCharsSequence();
    List<DiffFragment> fragments = myComparisonManager.compareChars(sequence1, sequence2, policy, INDICATOR);
    checkConsistency(fragments, before, after, policy);
    if (matchings != null) checkDiffMatching(fragments, before, after, matchings, policy);
    if (expected != null) checkDiffChanges(fragments, before, after, expected, policy);
  }

  private static void doSplitterTest(@NotNull Document before,
                                     @NotNull Document after,
                                     @Nullable Couple<BitSet> matchings,
                                     @Nullable List<Change> expected,
                                     @NotNull ComparisonPolicy policy) {
    CharSequence sequence1 = before.getCharsSequence();
    CharSequence sequence2 = after.getCharsSequence();
    List<LineFragment> fragments = myComparisonManager.compareLinesInner(sequence1, sequence2, policy, INDICATOR);
    checkConsistency(fragments, before, after, policy);
    if (matchings != null) checkLineMatching(fragments, before, after, matchings, policy);
    if (expected != null) checkLineChanges(fragments, before, after, expected, policy);
  }

  private static void checkConsistencyWord(@NotNull List<LineFragment> fragments,
                                           @NotNull Document before,
                                           @NotNull Document after,
                                           @NotNull ComparisonPolicy policy) {
    assertTrue(fragments.size() == 1);
    LineFragment fragment = fragments.get(0);
    List<DiffFragment> diffFragments = fragment.getInnerFragments();
    assertNotNull(diffFragments); // It could be null if there are no common words. We do not test such cases here.

    assertTrue(fragment.getStartOffset1() == 0 &&
               fragment.getStartOffset2() == 0 &&
               fragment.getEndOffset1() == before.getTextLength() &&
               fragment.getEndOffset2() == after.getTextLength());

    checkConsistency(diffFragments, before, after, policy);
  }

  private static void checkConsistency(@NotNull List<? extends DiffFragment> fragments,
                                       @NotNull Document before,
                                       @NotNull Document after,
                                       @NotNull ComparisonPolicy policy) {
    for (DiffFragment fragment : fragments) {
      assertTrue(fragment.getStartOffset1() <= fragment.getEndOffset1());
      assertTrue(fragment.getStartOffset2() <= fragment.getEndOffset2());

      if (fragment instanceof LineFragment) {
        LineFragment lineFragment = (LineFragment)fragment;
        assertTrue(lineFragment.getStartLine1() <= lineFragment.getEndLine1());
        assertTrue(lineFragment.getStartLine2() <= lineFragment.getEndLine2());

        assertTrue(lineFragment.getStartLine1() != lineFragment.getEndLine1() || lineFragment.getStartLine2() != lineFragment.getEndLine2());

        assertTrue(lineFragment.getStartLine1() >= 0);
        assertTrue(lineFragment.getStartLine2() >= 0);
        assertTrue(lineFragment.getEndLine1() <= getLineCount(before));
        assertTrue(lineFragment.getEndLine2() <= getLineCount(after));

        checkLineOffsets(lineFragment, before, after, policy);

        if (lineFragment.getInnerFragments() != null) checkConsistency(lineFragment.getInnerFragments(), before, after, policy);
      } else {
        assertTrue(fragment.getStartOffset1() != fragment.getEndOffset1() || fragment.getStartOffset2() != fragment.getEndOffset2());
      }
    }
  }

  private static void checkLineChanges(@NotNull List<? extends LineFragment> fragments,
                                       @NotNull Document before,
                                       @NotNull Document after,
                                       @NotNull List<Change> expected,
                                       @NotNull ComparisonPolicy policy) {
    List<Change> changes = convertLineFragments(fragments);
    assertOrderedEquals(policy.name(), changes, expected);
  }

  private static void checkDiffChanges(@NotNull List<? extends DiffFragment> fragments,
                                           @NotNull Document before,
                                           @NotNull Document after,
                                           @NotNull List<Change> expected,
                                           @NotNull ComparisonPolicy policy) {
    List<Change> changes = convertDiffFragments(fragments);
    assertOrderedEquals(policy.name(), changes, expected);
  }

  private static void checkLineMatching(@NotNull List<? extends LineFragment> fragments,
                                        @NotNull Document before,
                                        @NotNull Document after,
                                        @NotNull Couple<BitSet> matchings,
                                        @NotNull ComparisonPolicy policy) {
    BitSet set1 = new BitSet();
    BitSet set2 = new BitSet();
    for (LineFragment fragment : fragments) {
      set1.set(fragment.getStartLine1(), fragment.getEndLine1());
      set2.set(fragment.getStartLine2(), fragment.getEndLine2());
    }

    assertEquals(policy.name(), set1, matchings.first);
    assertEquals(policy.name(), set2, matchings.second);
  }

  private static void checkDiffMatching(@NotNull List<? extends DiffFragment> fragments,
                                        @NotNull Document before,
                                        @NotNull Document after,
                                        @NotNull Couple<BitSet> matchings,
                                        @NotNull ComparisonPolicy policy) {
    BitSet set1 = new BitSet();
    BitSet set2 = new BitSet();
    for (DiffFragment fragment : fragments) {
      set1.set(fragment.getStartOffset1(), fragment.getEndOffset1());
      set2.set(fragment.getStartOffset2(), fragment.getEndOffset2());
    }

    assertEquals(policy.name(), set1, matchings.first);
    assertEquals(policy.name(), set2, matchings.second);
  }

  @NotNull
  private static List<Change> convertDiffFragments(@NotNull List<? extends DiffFragment> fragments) {
    return ContainerUtil.map(fragments, new Function<DiffFragment, Change>() {
      @Override
      public Change fun(DiffFragment fragment) {
        return new Change(fragment.getStartOffset1(), fragment.getEndOffset1(), fragment.getStartOffset2(), fragment.getEndOffset2());
      }
    });
  }

  @NotNull
  private static List<Change> convertLineFragments(@NotNull List<? extends LineFragment> fragments) {
    return ContainerUtil.map(fragments, new Function<LineFragment, Change>() {
      @Override
      public Change fun(LineFragment fragment) {
        return new Change(fragment.getStartLine1(), fragment.getEndLine1(), fragment.getStartLine2(), fragment.getEndLine2());
      }
    });
  }

  private static void checkLineOffsets(@NotNull LineFragment fragment,
                                       @NotNull Document before,
                                       @NotNull Document after,
                                       @NotNull ComparisonPolicy policy) {
    checkLineOffsets(before, fragment.getStartLine1(), fragment.getEndLine1(),
                     fragment.getStartOffset1(), fragment.getEndOffset1(), policy);

    checkLineOffsets(after, fragment.getStartLine2(), fragment.getEndLine2(),
                     fragment.getStartOffset2(), fragment.getEndOffset2(), policy);
  }

  private static void checkLineOffsets(@NotNull Document document,
                                       int startLine,
                                       int endLine,
                                       int startOffset,
                                       int endOffset,
                                       @NotNull ComparisonPolicy policy) {
    if (startLine != endLine) {
      assertEquals(policy.name(), document.getLineStartOffset(startLine), startOffset);
      int offset = document.getLineEndOffset(endLine - 1);
      if (offset < document.getTextLength()) offset++;
      assertEquals(policy.name(), offset, endOffset);
    }
    else {
      int offset = startLine == getLineCount(document)
                   ? document.getTextLength()
                   : document.getLineStartOffset(startLine);
      assertEquals(policy.name(), offset, startOffset);
      assertEquals(policy.name(), offset, endOffset);
    }
  }

  //
  // Test Builder
  //

  public static class TestData {
    enum TestType {LINE, WORD, CHAR, SPLITTER}

    @NotNull private final TestType myType;
    @NotNull private final String myBefore;
    @NotNull private final String myAfter;

    @Nullable private List<Change> myDefaultChanges;
    @Nullable private List<Change> myTrimChanges;
    @Nullable private List<Change> myIgnoreChanges;

    @Nullable private Couple<BitSet> myDefaultMatching;
    @Nullable private Couple<BitSet> myTrimMatching;
    @Nullable private Couple<BitSet> myIgnoreMatching;

    private boolean mySkipDefault;
    private boolean mySkipTrim;
    private boolean mySkipIgnore;

    public TestData(@NotNull TestType type, @NotNull String before, @NotNull String after) {
      myType = type;
      myBefore = before;
      myAfter = after;
    }

    @NotNull
    public static TestData lines(@NotNull String before, @NotNull String after) {
      return new TestData(TestType.LINE, before, after);
    }

    @NotNull
    public static TestData words(@NotNull String before, @NotNull String after) {
      return new TestData(TestType.WORD, before, after);
    }

    @NotNull
    public static TestData chars(@NotNull String before, @NotNull String after) {
      TestData data = new TestData(TestType.CHAR, before, after);
      data.mySkipTrim = true; // Not supported
      return data;
    }

    @NotNull
    public static TestData split(@NotNull String before, @NotNull String after) {
      return new TestData(TestType.SPLITTER, before, after);
    }

    @NotNull
    public TestData _______Def_(@NotNull String before, @NotNull String after) {
      assert myBefore.length() == before.length();
      assert myAfter.length() == after.length();
      myDefaultMatching = parseMatching(before, after);
      return this;
    }

    @NotNull
    public TestData ______Trim_(@NotNull String before, @NotNull String after) {
      assert myType != TestType.CHAR;
      assert myBefore.length() == before.length();
      assert myAfter.length() == after.length();
      myTrimMatching = parseMatching(before, after);
      return this;
    }

    @NotNull
    public TestData ____Ignore_(@NotNull String before, @NotNull String after) {
      assert myBefore.length() == before.length();
      assert myAfter.length() == after.length();
      myIgnoreMatching = parseMatching(before, after);
      return this;
    }

    @NotNull
    public TestData _Def_(Change... expected) {
      myDefaultChanges = ContainerUtil.list(expected);
      return this;
    }

    @NotNull
    public TestData _Trim_(Change... expected) {
      myTrimChanges = ContainerUtil.list(expected);
      return this;
    }

    @NotNull
    public TestData _Ignore_(Change... expected) {
      myIgnoreChanges = ContainerUtil.list(expected);
      return this;
    }

    public void all() {
      run();
    }

    public void skipDef() {
      mySkipDefault = true;
      run();
    }

    public void skipTrim() {
      mySkipTrim = true;
      run();
    }

    public void skipIgnore() {
      mySkipIgnore = true;
      run();
    }

    public void def() {
      mySkipTrim = true;
      mySkipIgnore = true;
      run();
    }

    public void trim() {
      mySkipDefault = true;
      mySkipIgnore = true;
      run();
    }

    public void ignore() {
      mySkipDefault = true;
      mySkipTrim = true;
      run();
    }

    @NotNull
    public Document getBefore() {
      return new DocumentImpl(myBefore.replace('_', '\n'));
    }

    @NotNull
    public Document getAfter() {
      return new DocumentImpl(myAfter.replace('_', '\n'));
    }

    @Nullable
    List<Change> getChange(@NotNull ComparisonPolicy policy) {
      switch (policy) {
        case IGNORE_WHITESPACES:
          if (myIgnoreChanges != null) return myIgnoreChanges;
        case TRIM_WHITESPACES:
          if (myTrimChanges != null) return myTrimChanges;
        case DEFAULT:
          if (myDefaultChanges != null) return myDefaultChanges;
      }
      return null;
    }

    @Nullable
    Couple<BitSet> getMatchings(@NotNull ComparisonPolicy policy) {
      switch (policy) {
        case IGNORE_WHITESPACES:
          if (myIgnoreMatching != null) return myIgnoreMatching;
        case TRIM_WHITESPACES:
          if (myTrimMatching != null) return myTrimMatching;
        case DEFAULT:
          if (myDefaultMatching != null) return myDefaultMatching;
      }
      return null;
    }

    private void run(@NotNull ComparisonPolicy policy) {
      List<Change> change = getChange(policy);
      Couple<BitSet> matchings = getMatchings(policy);

      switch (myType) {
        case LINE:
          doLineTest(getBefore(), getAfter(), matchings, change, policy);
          break;
        case WORD:
          doWordTest(getBefore(), getAfter(), matchings, change, policy);
          break;
        case CHAR:
          doCharTest(getBefore(), getAfter(), matchings, change, policy);
          break;
        case SPLITTER:
          doSplitterTest(getBefore(), getAfter(), matchings, change, policy);
          break;
      }
    }

    private void run() {
      if (!mySkipDefault) run(ComparisonPolicy.DEFAULT);
      if (!mySkipTrim) run(ComparisonPolicy.TRIM_WHITESPACES);
      if (!mySkipIgnore) run(ComparisonPolicy.IGNORE_WHITESPACES);
    }
  }

  @NotNull
  public static Couple<BitSet> parseMatching(@NotNull String before, @NotNull String after) {
    return Couple.of(parseMatching(before), parseMatching(after));
  }

  @NotNull
  public static BitSet parseMatching(@NotNull String matching) {
    BitSet set = new BitSet();
    for (int i = 0; i < matching.length(); i++) {
      if (matching.charAt(i) != ' ') set.set(i);
    }
    return set;
  }

  public static Change mod(int line1, int line2, int count1, int count2) {
    assert count1 != 0;
    assert count2 != 0;
    return new Change(line1, line1 + count1, line2, line2 + count2);
  }

  public static Change del(int line1, int line2, int count1) {
    assert count1 != 0;
    return new Change(line1, line1 + count1, line2, line2);
  }

  public static Change ins(int line1, int line2, int count2) {
    assert count2 != 0;
    return new Change(line1, line1, line2, line2 + count2);
  }

  public static class Change {
    public int start1;
    public int end1;
    public int start2;
    public int end2;

    public Change(int start1, int end1, int start2, int end2) {
      this.start1 = start1;
      this.end1 = end1;
      this.start2 = start2;
      this.end2 = end2;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Change change = (Change)o;

      if (end1 != change.end1) return false;
      if (end2 != change.end2) return false;
      if (start1 != change.start1) return false;
      if (start2 != change.start2) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = start1;
      result = 31 * result + end1;
      result = 31 * result + start2;
      result = 31 * result + end2;
      return result;
    }

    @Override
    public String toString() {
      return "(" + start1 + ", " + end1 + ") - (" + start2 + ", " + end2 + ")";
    }
  }

  private static int getLineCount(@NotNull Document document) {
    return Math.max(1, document.getLineCount());
  }
}
