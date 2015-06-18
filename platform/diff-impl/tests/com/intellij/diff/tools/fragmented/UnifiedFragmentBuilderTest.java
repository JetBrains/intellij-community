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

import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonManagerImpl;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.util.LineRange;
import com.intellij.diff.util.Side;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.testFramework.UsefulTestCase;

import java.util.Collections;
import java.util.List;

public class UnifiedFragmentBuilderTest extends UsefulTestCase {
  private static ComparisonManager myComparisonManager = new ComparisonManagerImpl();

  public void testEquals() {
    Document document1 = new DocumentImpl("A\nB\nC");
    Document document2 = new DocumentImpl("A\nB\nC");

    List<LineFragment> fragments = myComparisonManager.compareLinesInner(document1.getCharsSequence(), document2.getCharsSequence(),
                                                                         ComparisonPolicy.DEFAULT, DumbProgressIndicator.INSTANCE);

    UnifiedFragmentBuilder builder = new UnifiedFragmentBuilder(fragments, document1, document2, Side.LEFT);
    builder.exec();

    assertTrue(builder.isEqual());
    assertEquals(builder.getText().toString(), "A\nB\nC\n");
    assertEmpty(builder.getChangedLines());
    assertEmpty(builder.getBlocks());
  }

  public void testWrongEndLineTypoBug() {
    Document document1 = new DocumentImpl("A\nB\nC\nD");
    Document document2 = new DocumentImpl("A\nD");

    List<LineFragment> fragments = myComparisonManager.compareLinesInner(document1.getCharsSequence(), document2.getCharsSequence(),
                                                                         ComparisonPolicy.DEFAULT, DumbProgressIndicator.INSTANCE);

    UnifiedFragmentBuilder builder = new UnifiedFragmentBuilder(fragments, document1, document2, Side.RIGHT);
    builder.exec();

    assertFalse(builder.isEqual());
    assertEquals(builder.getText().toString(), "A\nB\nC\nD\n");
    assertEquals(builder.getChangedLines(), Collections.singletonList(new LineRange(1, 3)));

    assertEquals(builder.getBlocks().size(), 1);
    ChangedBlock block = builder.getBlocks().get(0);
    assertEquals(block.getLine1(), 1);
    assertEquals(block.getLine2(), 3);
    assertEquals(block.getStartOffset1(), 2);
    assertEquals(block.getEndOffset1(), 6);
    assertEquals(block.getStartOffset2(), 6);
    assertEquals(block.getEndOffset2(), 6);
  }
}
