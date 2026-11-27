/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ig.fixes.style;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.style.LambdaParameterTypeCanBeSpecifiedInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class LambdaParameterTypeCanBeSpecifiedFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new LambdaParameterTypeCanBeSpecifiedInspection()), "style/inferLambdaParameterType");

  @Test
  public void testSimple() {
    ig.get().doTest(getHintName("(String o)"));
  }

  @Test
  public void testFile() {
    ig.get().doTest(getHintName("(File o)"));
  }

  @Test
  public void testFileOutOfFixRange() {
    ig.get().assertQuickfixNotAvailable(getHintName("(File o)"));
  }

  @Test
  public void testTwoParams() {
    ig.get().doTest(getHintName("(String o1, String o2)"));
  }

  @Test
  public void testSimpleWildcard() {
    ig.get().doTest(getHintName("(Integer o)"));
  }

  @Test
  public void testAlreadyExist() {
    ig.get().assertQuickfixNotAvailable(getHintName("(String o)"));
  }

  @Test
  public void testCyclicInference() {
    ig.get().assertQuickfixNotAvailable(getHintName("(Object x)"));
  }

  @Test
  public void testNoParams() {
    ig.get().assertQuickfixNotAvailable(getHintName("()"));
  }

  @Test
  public void testCapturedWildcardParams() {
    ig.get().assertQuickfixNotAvailable(getHintName("(capture of ?)"));
  }

  private static String getHintName(String paramList) {
    return InspectionGadgetsBundle.message("lambda.parameter.type.can.be.specified.quickfix", paramList);
  }

}
