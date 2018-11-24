/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.RangeSplitter;
import com.intellij.openapi.util.TextRange;
import junit.framework.TestCase;

import java.util.Arrays;
import java.util.List;

public class RangeSplitterTest extends TestCase {
  public void testSplitWithOne() {
    TextRange big = new TextRange(10, 100);
    TextRange small = new TextRange(50, 60);

    List<TextRange> result = split(big, Arrays.asList(small));
    assertEquals(2, result.size());
    assertEquals(result.toString(), new TextRange(10, 50), result.get(0));
    assertEquals(result.toString(), new TextRange(60, 100), result.get(1));
  }

  public void testSplitWithTwo() {
    TextRange big = new TextRange(10, 100);
    TextRange one = new TextRange(20, 30);
    TextRange two = new TextRange(60, 70);

    List<TextRange> result = split(big, Arrays.asList(one, two));

    assertEquals(3, result.size());
    assertEquals(result.toString(), new TextRange(10, 20), result.get(0));
    assertEquals(result.toString(), new TextRange(30, 60), result.get(1));
    assertEquals(result.toString(), new TextRange(70, 100), result.get(2));
  }

  public void testOnTheEnge() {
    TextRange big = new TextRange(10, 100);
    TextRange one = new TextRange(10, 20);
    TextRange two = new TextRange(90, 100);

    List<TextRange> result = split(big, Arrays.asList(one, two));
    assertEquals(result.toString(), 1, result.size());
    assertEquals(result.toString(), new TextRange(20, 90), result.get(0));
  }

  public void testSplitByToNeighbour() {
    TextRange big = new TextRange(10, 100);
    TextRange one = new TextRange(20, 40);
    TextRange two = new TextRange(40, 50);

    List<TextRange> result = split(big, Arrays.asList(one, two));
    assertEquals(result.toString(), 2, result.size());
    assertEquals(result.toString(), new TextRange(10, 20), result.get(0));
    assertEquals(result.toString(), new TextRange(50, 100), result.get(1));
  }

  public void testSplitByWholeOne() {
    TextRange big = new TextRange(10, 100);
    TextRange devider = new TextRange(10, 100);

    List<TextRange> result = split(big, Arrays.asList(devider));
    assertTrue(result.toString(), result.isEmpty());
  }

  public static List<TextRange> split(TextRange target, List<TextRange> deviders) {
    return RangeSplitter.split(target, deviders);
  }
}
