/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.bugs.StaticCallOnSubclassInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class StaticCallOnSubclassTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new StaticCallOnSubclassInspection()), "bugs/staticCallOnSubclass");

  @Test
  public void testPreserveTypeArgs() {
    ig.get().doTest("preserveTypeArgs", InspectionGadgetsBundle.message("static.method.via.subclass.rationalize.quickfix"));
  }

  @Test
  public void testMethodReference() {
    ig.get().doTest("methodRef", InspectionGadgetsBundle.message("static.method.via.subclass.rationalize.quickfix"));
  }
  
  @Test
  public void testOnInterface() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("static.method.via.subclass.rationalize.quickfix"));
  }

}
