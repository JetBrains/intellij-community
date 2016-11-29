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

/*
  (c) 2016 Silent Forest AB
  created: 18 March 2016
 */
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.concurrencyAnnotations.UnknownGuardInspection;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class UnknownGuardInspectionTest extends LightCodeInsightFixtureTestCase {

  public void testUnknownGuard() {
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".java");
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UnknownGuardInspection());
    myFixture.addClass("package javax.annotation.concurrent;\n" +
                       "@java.lang.annotation.Target({java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.METHOD})\n" +
                       "@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)\n" +
                       "public @interface GuardedBy {\n" +
                       "    java.lang.String value();\n" +
                       "}");
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/unknownGuard";
  }
}
