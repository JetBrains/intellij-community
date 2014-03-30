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

package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.concurrencyAnnotations.FieldAccessNotGuardedInspection;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class FieldAccessedNotGuardedInspectionTest extends LightCodeInsightFixtureTestCase {
  public void testItself() throws Exception {
    myFixture.addClass("package net.jcip.annotations;\n" +
                       "@java.lang.annotation.Target({java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.METHOD})\n" +
                       "@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)\n" +
                       "public @interface GuardedBy {\n" +
                       "    java.lang.String value();\n" +
                       "}");
    myFixture.testHighlighting(true, false, false, getTestName(true) + ".java");
  }


  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new FieldAccessNotGuardedInspection());
  }

  @NotNull
   @Override
   protected String getTestDataPath() {
     return JavaTestUtil.getJavaTestDataPath() + "/inspection/guarded";
   }
}
