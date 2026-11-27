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
package com.siyeh.ig.fixes.equality;

import com.intellij.pom.java.LanguageLevel;
import com.siyeh.InspectionGadgetsBundle;
import org.junit.jupiter.api.Test;

public class EqualityOperatorComparesObjectsInspectionJdk6Test extends EqualityOperatorComparesObjectsInspectionTestBase {
  public EqualityOperatorComparesObjectsInspectionJdk6Test() {
    super(builder -> builder.addJdkVersion(LanguageLevel.JDK_1_7)
      .setLanguageLevel(LanguageLevel.JDK_1_6));
  }

  @Test
  public void testSimpleObjectOldSafeComparison() { ig.get().doTest(InspectionGadgetsBundle.message("equality.to.safe.equals.quickfix")); }
  @Test
  public void testNegatedObjectOldSafeComparison() { ig.get().doTest(InspectionGadgetsBundle.message("inequality.to.safe.not.equals.quickfix")); }
}
