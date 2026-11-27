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
package com.siyeh.ig.fixes.bugs;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.bugs.CastConflictsWithInstanceofInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author anna
 */
@TestApplication
public class CastConflictsWithInstanceofFixesTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new CastConflictsWithInstanceofInspection()), "bugs/castConflicts");

  @Test
  public void testReplaceCastInDeclaration() {
    ig.get().doTest("replaceCastInDeclaration", "Replace 'Integer' with 'String' in cast");
  }

  @Test
  public void testReplaceInstanceOf() {
    ig.get().doTest("replaceInstanceOf", "Replace 'String' with 'Integer' in instanceof");
  }

  @Test
  public void testIgnoreUncheckedCastToTypeParameter() {
    ig.get().assertQuickfixNotAvailable("Replace 'E' with 'String' in cast");
  }

  @Test
  public void testReplaceInstanceofInFront() {
    ig.get().doTest("replaceInstanceofInFront", "Replace 'String' with 'Integer' in instanceof");
  }

}
