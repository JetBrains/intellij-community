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

// TODO
public class SplitComparisonUtilTest extends ComparisonUtilTestBase {
  public void testSplitter() {
    TestData.split("x", "z")._Def_(mod(0, 0, 1, 1)).all();
    TestData.split("x_y", "a_b")._Def_(mod(0, 0, 2, 2)).all();
    TestData.split("x_y", "a_b y")._Def_(mod(0, 0, 1, 1), mod(1, 1, 1, 1)).all();

    TestData.split("a_x_b_", " x_")._Def_(del(0, 0, 1), mod(1, 0, 1, 1), del(2, 1, 1)).def();
    TestData.split("a_x_b_", "!x_")._Def_(del(0, 0, 1), mod(1, 0, 1, 1), del(2, 1, 1)).all();
  }
}
