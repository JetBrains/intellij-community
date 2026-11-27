/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.fixes.errorhandling;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.errorhandling.ThrowsRuntimeExceptionInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class ThrowsRuntimeExceptionInspectionFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new ThrowsRuntimeExceptionInspection()), "errorhandling/throws_runtime_exception");

  @Test
  public void testMoveThrows() {
    ig.get().doTest(InspectionGadgetsBundle.message("throws.runtime.exception.move.quickfix", "RuntimeException"));
  }

  @Test
  public void testMoveThrowsExistingComment() {
    ig.get().doTest(InspectionGadgetsBundle.message("throws.runtime.exception.move.quickfix", "RuntimeException"));
  }

  @Test
  public void testMoveThrowsExistingCommentMarkdown() {
    ig.get().doTest(InspectionGadgetsBundle.message("throws.runtime.exception.move.quickfix", "RuntimeException"));
  }

  @Test
  public void testRemoveThrows() {
    ig.get().doTest(InspectionGadgetsBundle.message("throws.runtime.exception.quickfix", "RuntimeException"));
  }
}
