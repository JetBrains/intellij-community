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
import com.siyeh.ig.style.ArrayCreationWithoutNewKeywordInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class ArrayCreationWithoutNewKeywordFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new ArrayCreationWithoutNewKeywordInspection()), "style/array_creation_expression");

    @Test
    public void testPrimitive() { doTest("int[]"); }
    @Test
    public void testGeneric() { doTest("java.util.Map[][]"); }
    @Test
    public void testNotAnArray() { assertQuickfixNotAvailable(); }

  private void doTest(String hint) {
    ig.get().doTest(InspectionGadgetsBundle.message("array.creation.without.new.keyword.quickfix", hint));
  }

  private void assertQuickfixNotAvailable() {
    String message = InspectionGadgetsBundle.message("array.creation.without.new.keyword.quickfix", "@");
    ig.get().assertQuickfixNotAvailable(message.substring(0, message.indexOf('@')));
  }

}
