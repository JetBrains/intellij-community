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
package com.intellij.formatting;

import com.intellij.openapi.editor.TextChange;
import com.intellij.openapi.editor.impl.softwrap.TextChangeImpl;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * @author Denis Zhdanov
 * @since 12/22/2010
 */
public class BulkChangesMergerTest {

  private BulkChangesMerger myMerger;  
    
  @Before
  public void setUp() {
    myMerger = new BulkChangesMerger();
  }
  
  @Test
  public void simpleReplaceInTheMiddle() {
    doTest("abcd", "a123d", c("123", 1, 3));
  }
  
  @Test
  public void disjointInserts() {
    doTest("abcd", "a1b2c3d45", c("1", 1), c("2", 2), c("3", 3), c("45", 4));
  }
  
  @Test
  public void interestedSymbolsNumberLessThanAvailable() {
    doTest("abcdefg", 4, "a12bc3d", c("12", 1), c("3", 3));
  }

  private static TextChange c(String text, int offset) {
    return c(text, offset, offset);
  }
  
  private static TextChange c(String text, int start, int end) {
    return new TextChangeImpl(text, start, end);
  }
  
  private void doTest(String initial, String expected, TextChange ... changes) {
    doTest(initial, initial.length(), expected, changes);
  }

  private void doTest(String initial, int interestedInitialSymbolsNumber, String expected, TextChange ... changes) {
    CharSequence actual = myMerger.merge(initial.toCharArray(), interestedInitialSymbolsNumber, Arrays.asList(changes));
    assertEquals(expected, actual.toString());
  }
}
