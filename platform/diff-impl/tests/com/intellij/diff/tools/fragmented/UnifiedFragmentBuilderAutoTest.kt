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
package com.intellij.diff.tools.fragmented;

import com.intellij.diff.comparison.AutoTestCase;
import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonManagerImpl;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.util.LineRange;
import com.intellij.diff.util.Side;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UnifiedFragmentBuilderAutoTest extends AutoTestCase {
  private static ComparisonManager myComparisonManager = new ComparisonManagerImpl();

  private static final int CHAR_COUNT = 12;
  private static final Map<Integer, Character> CHAR_TABLE = initCharMap();

  public void test() throws Exception {
    doTest(System.currentTimeMillis(), 30, 30);
  }

  public void doTest(long seed, int runs, int maxLength) throws Exception {
    myRng.setSeed(seed);

    ComparisonPolicy policy = null;
    Side masterSide = null;

    for (int i = 0; i < runs; i++) {
      if (i % 1000 == 0) System.out.println(i);
      Document text1 = null;
      Document text2 = null;
      try {
        rememberSeed();

        text1 = generateText(maxLength);
        text2 = generateText(maxLength);

        for (Side side : Side.values()) {
          for (ComparisonPolicy comparisonPolicy : ComparisonPolicy.values()) {
            policy = comparisonPolicy;
            masterSide = side;
            doTest(text1, text2, policy, masterSide);
          }
        }
      }
      catch (Throwable e) {
        System.out.println("Seed: " + seed);
        System.out.println("Runs: " + runs);
        System.out.println("MaxLength: " + maxLength);
        System.out.println("Policy: " + policy);
        System.out.println("Current side: " + masterSide);
        System.out.println("I: " + i);
        System.out.println("Current seed: " + getLastSeed());
        System.out.println("Text1: " + textToReadableFormat(text1));
        System.out.println("Text2: " + textToReadableFormat(text2));
        if (e instanceof Error) throw (Error)e;
        if (e instanceof Exception) throw (Exception)e;
        throw new Exception(e);
      }
    }
  }

  public void doTest(@NotNull Document document1, @NotNull Document document2, @NotNull ComparisonPolicy policy, @NotNull Side masterSide) {
    CharSequence sequence1 = document1.getCharsSequence();
    CharSequence sequence2 = document2.getCharsSequence();

    List<LineFragment> fragments = myComparisonManager.compareLinesInner(sequence1, sequence2,
                                                                         policy, DumbProgressIndicator.INSTANCE);

    UnifiedFragmentBuilder builder = new UnifiedFragmentBuilder(fragments, document1, document2, masterSide);
    builder.exec();

    boolean ignoreWhitespaces = policy != ComparisonPolicy.DEFAULT;
    CharSequence text = builder.getText();
    List<ChangedBlock> blocks = builder.getBlocks();
    LineNumberConvertor convertor = builder.getConvertor();
    List<LineRange> changedLines = builder.getChangedLines();
    List<HighlightRange> ranges = builder.getRanges();

    // both documents - before and after - should be subsequence of result text.
    assertTrue(isSubsequence(text, sequence1, ignoreWhitespaces));
    assertTrue(isSubsequence(text, sequence2, ignoreWhitespaces));

    // all changes should be inside ChangedLines
    for (LineFragment fragment : fragments) {
      int startLine1 = fragment.getStartLine1();
      int endLine1 = fragment.getEndLine1();
      int startLine2 = fragment.getStartLine2();
      int endLine2 = fragment.getEndLine2();

      for (int i = startLine1; i < endLine1; i++) {
        int targetLine = convertor.convertInv1(i);
        assertTrue(targetLine != -1);
        assertTrue(isLineChanged(targetLine, changedLines));
      }
      for (int i = startLine2; i < endLine2; i++) {
        int targetLine = convertor.convertInv2(i);
        assertTrue(targetLine != -1);
        assertTrue(isLineChanged(targetLine, changedLines));
      }
    }

    // changed fragments and changed blocks should have same content
    assertEquals(blocks.size(), fragments.size());
    for (int i = 0; i < fragments.size(); i++) {
      LineFragment fragment = fragments.get(i);
      ChangedBlock block = blocks.get(i);

      CharSequence fragment1 = sequence1.subSequence(fragment.getStartOffset1(), fragment.getEndOffset1());
      CharSequence fragment2 = sequence2.subSequence(fragment.getStartOffset2(), fragment.getEndOffset2());

      CharSequence block1 = text.subSequence(block.getStartOffset1(), block.getEndOffset1());
      CharSequence block2 = text.subSequence(block.getStartOffset2(), block.getEndOffset2());

      assertEqualsCharSequences(fragment1, block1, ignoreWhitespaces, true);
      assertEqualsCharSequences(fragment2, block2, ignoreWhitespaces, true);
    }

    // ranges should have exact same content
    for (HighlightRange range : ranges) {
      CharSequence sideSequence = range.getSide().select(sequence1, sequence2);
      CharSequence baseRange = text.subSequence(range.getBase().getStartOffset(), range.getBase().getEndOffset());
      CharSequence sideRange = sideSequence.subSequence(range.getChanged().getStartOffset(), range.getChanged().getEndOffset());
      assertTrue(StringUtil.equals(baseRange, sideRange));
    }
  }

  private static boolean isSubsequence(@NotNull CharSequence text, @NotNull CharSequence sequence, boolean ignoreWhitespaces) {
    int index1 = 0;
    int index2 = 0;

    while (index2 < sequence.length()) {
      char c2 = sequence.charAt(index2);
      if (c2 == '\n' ||
          StringUtil.isWhiteSpace(c2) && ignoreWhitespaces) {
        index2++;
        continue;
      }

      assertTrue(index1 < text.length());
      char c1 = text.charAt(index1);
      if (c1 == '\n' ||
          StringUtil.isWhiteSpace(c1) && ignoreWhitespaces) {
        index1++;
        continue;
      }

      if (c1 == c2) {
        index1++;
        index2++;
      }
      else {
        index1++;
      }
    }

    return true;
  }

  private static boolean isLineChanged(int line, @NotNull List<LineRange> changedLines) {
    for (LineRange changedLine : changedLines) {
      if (changedLine.start <= line && changedLine.end > line) return true;
    }
    return false;
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
}
